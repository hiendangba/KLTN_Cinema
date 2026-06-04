#!/usr/bin/env bash
set -euo pipefail

# Import 100 bookings + 100 payment transactions by reading live data from the
# existing service databases inside the postgres container.
#
# Usage:
#   PG_CONTAINER=pg ./scripts/import-booking-payment.sh
#   SHOWTIME_DATE=2026-07-11 PG_CONTAINER=pg ./scripts/import-booking-payment.sh
#
# Optional overrides:
#   CUSTOMER_LIMIT=100
#   BOOKING_LIMIT=100
#   SHOWTIME_DATE=YYYY-MM-DD

PG_CONTAINER="${PG_CONTAINER:-pg}"
CUSTOMER_LIMIT="${CUSTOMER_LIMIT:-100}"
BOOKING_LIMIT="${BOOKING_LIMIT:-100}"

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

run_psql_query() {
  local db="$1"
  local sql="$2"
  docker exec -i "$PG_CONTAINER" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -At -F $'\t' -c "$sql"
}

run_psql_file() {
  local db="$1"
  local file="$2"
  docker exec -i "$PG_CONTAINER" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 < "$file"
}

run_psql_query user_db "
SELECT id, email, name, COALESCE(phone, '')
FROM users
WHERE is_deleted = false
  AND role = 'CUSTOMER'
ORDER BY email
LIMIT ${CUSTOMER_LIMIT};
" > "${TMP_DIR}/customers.tsv"

run_psql_query showtime_db "
SELECT id, hall_id, film_id, start_date_time, end_date_time
FROM show_time
WHERE is_deleted = false
  AND status IN ('SCHEDULED', 'ONGOING')
$(if [[ -n "${SHOWTIME_DATE:-}" ]]; then printf "  AND start_date_time::date = DATE '%s'\n" "${SHOWTIME_DATE}"; fi)
ORDER BY start_date_time, id
LIMIT ${BOOKING_LIMIT};
" > "${TMP_DIR}/showtimes.tsv"

run_psql_query hall_db "
SELECT id, cinema_id
FROM hall
WHERE is_deleted = false
ORDER BY id;
" > "${TMP_DIR}/halls.tsv"

run_psql_query seat_db "
SELECT id, hall_id, seat_code, seat_row, seat_col, seat_type
FROM seat
WHERE is_deleted = false
ORDER BY hall_id, seat_row, seat_col;
" > "${TMP_DIR}/seats.tsv"

run_psql_query showtime_db "
SELECT id, cinema_id, standard_price, vip_price, couple_price
FROM pricing_policy
WHERE is_deleted = false
ORDER BY cinema_id, time_created DESC, id DESC;
" > "${TMP_DIR}/pricing.tsv"

run_psql_query film_db "
SELECT id, title
FROM films
WHERE is_deleted = false
ORDER BY id;
" > "${TMP_DIR}/films.tsv"

PYTHON_BIN="$(command -v python3 || command -v python || true)"
if [[ -z "$PYTHON_BIN" ]]; then
  echo "Python is required but neither python3 nor python was found in PATH." >&2
  exit 1
fi

"$PYTHON_BIN" - "${TMP_DIR}" <<'PY'
import csv
import json
import os
import sys
import uuid
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timedelta
from pathlib import Path

tmp_dir = Path(sys.argv[1])


def read_tsv(name):
    path = tmp_dir / name
    rows = []
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        for row in reader:
            if row:
                rows.append(row)
    return rows


def sql_text(value):
    if value is None or value == "":
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def sql_uuid(value):
    return f"'{value}'::uuid"


def sql_ts(value):
    return f"'{value.strftime('%Y-%m-%d %H:%M:%S')}'::timestamp"


def sql_decimal(value):
    return f"{value.quantize(Decimal('0.01'))}"


def parse_dt(value):
    return datetime.fromisoformat(value.strip())


def render_upsert(table, columns, rows):
    if not rows:
        raise SystemExit(f"No rows generated for {table}")

    column_list = ", ".join(columns)
    values_sql = ",\n".join(f"  ({', '.join(row)})" for row in rows)
    update_clause = ",\n    ".join(
        f"{column} = EXCLUDED.{column}" for column in columns if column != "id"
    )

    return (
        "INSERT INTO "
        + table
        + " ("
        + column_list
        + ")\nVALUES\n"
        + values_sql
        + "\nON CONFLICT (id) DO UPDATE SET\n    "
        + update_clause
        + ";\n"
    )


def render_values_update(table, rows, set_sql):
    if not rows:
        raise SystemExit(f"No rows generated for {table}")

    values_sql = ",\n".join(
        f"  ({sql_uuid(row_id)}, {sql_ts(paid_at)})" for row_id, paid_at in rows
    )
    return (
        f"UPDATE {table} AS t\n"
        f"SET {set_sql}\n"
        f"FROM (\nVALUES\n{values_sql}\n) AS v(id, event_at)\n"
        f"WHERE t.id = v.id;\n"
    )


customers = read_tsv("customers.tsv")
showtimes = read_tsv("showtimes.tsv")
halls = read_tsv("halls.tsv")
seats = read_tsv("seats.tsv")
pricing_rows = read_tsv("pricing.tsv")
films = read_tsv("films.tsv")
showtime_date_filter = os.environ.get("SHOWTIME_DATE", "").strip() or None

if len(customers) < 100:
    raise SystemExit(f"Need at least 100 customers, got {len(customers)}")
if not halls:
    raise SystemExit("No halls found")
if not seats:
    raise SystemExit("No seats found")
if not pricing_rows:
    raise SystemExit("No pricing policies found")
if not films:
    raise SystemExit("No films found")

if not showtimes:
    if showtime_date_filter:
        raise SystemExit(f"No active showtimes found on {showtime_date_filter}")
    raise SystemExit("No active showtimes found")

hall_to_cinema = {}
for hall_id, cinema_id in halls:
    hall_to_cinema[hall_id] = cinema_id

seats_by_hall = defaultdict(list)
for seat_id, hall_id, seat_code, seat_row, seat_col, seat_type in seats:
    seats_by_hall[hall_id].append(
        {
            "seat_id": seat_id,
            "hall_id": hall_id,
            "seat_code": seat_code,
            "seat_row": int(seat_row),
            "seat_col": int(seat_col),
            "seat_type": seat_type,
        }
    )

pricing_by_cinema = {}
for _policy_id, cinema_id, standard_price, vip_price, couple_price in pricing_rows:
    if cinema_id not in pricing_by_cinema:
        pricing_by_cinema[cinema_id] = {
            "standard": Decimal(standard_price),
            "vip": Decimal(vip_price),
            "couple": Decimal(couple_price),
        }

film_title_by_id = {film_id: title for film_id, title in films}
script_now = datetime.now()

promotion_definitions = [
    {
        "code": "SEED-DISCOUNT-01",
        "name": "Seed Discount",
        "description": "Seed promotion for booking/payment import.",
        "discount_type": "PERCENT",
        "discount_value": Decimal("10"),
        "min_order_amount": Decimal("0.00"),
        "max_discount_amount": Decimal("20000.00"),
        "created_by_role": "SYSTEM",
    }
]

promotion_by_code = {}
promotion_seed_rows = []
for promotion in promotion_definitions:
    promotion_id = uuid.uuid5(uuid.NAMESPACE_URL, f"seed-promotion-{promotion['code']}")
    promotion_by_code[promotion["code"]] = {
        "id": promotion_id,
        "code": promotion["code"],
        "name": promotion["name"],
        "discount_type": promotion["discount_type"],
        "discount_value": promotion["discount_value"],
        "max_discount_amount": promotion["max_discount_amount"],
    }
    promotion_seed_rows.append(
        [
            sql_uuid(str(promotion_id)),
            sql_text(promotion["code"]),
            sql_text(promotion["name"]),
            sql_text(promotion["description"]),
            sql_text(promotion["discount_type"]),
            sql_decimal(promotion["discount_value"]),
            sql_decimal(promotion["min_order_amount"]),
            "NULL" if promotion["max_discount_amount"] is None else sql_decimal(promotion["max_discount_amount"]),
            "'ACTIVE'",
            sql_ts(script_now - timedelta(days=30)),
            sql_ts(script_now + timedelta(days=60)),
            "NULL",
            sql_text(promotion["created_by_role"]),
            "false",
            sql_ts(script_now),
            sql_ts(script_now),
        ]
    )

def price_for_seat_type(seat_type, pricing):
    normalized = seat_type.strip().upper()
    if normalized == "VIP":
        return pricing["vip"]
    if normalized == "COUPLE":
        return pricing["couple"]
    return pricing["standard"]


booking_insert_rows = []
booking_seat_insert_rows = []
payment_insert_rows = []
payment_promotion_insert_rows = []
booking_final_updates = []
payment_final_updates = []
used_seats_by_showtime = defaultdict(set)

for idx in range(100):
    customer_id, customer_email, customer_name, customer_phone = customers[idx]
    showtime_id, hall_id, film_id, start_text, end_text = showtimes[idx % len(showtimes)]

    cinema_id = hall_to_cinema.get(hall_id)
    if cinema_id is None:
        raise SystemExit(f"Missing cinema mapping for hall {hall_id}")

    pricing = pricing_by_cinema.get(cinema_id)
    if pricing is None:
        raise SystemExit(f"Missing pricing policy for cinema {cinema_id}")

    hall_seats = seats_by_hall.get(hall_id)
    if not hall_seats:
        raise SystemExit(f"Missing seats for hall {hall_id}")

    seat_count = 1 if idx % 2 == 0 else 2
    available_seats = [
        seat for seat in hall_seats
        if seat["seat_id"] not in used_seats_by_showtime[showtime_id]
    ]
    if len(available_seats) < seat_count:
        seat_count = 1
        available_seats = [
            seat for seat in hall_seats
            if seat["seat_id"] not in used_seats_by_showtime[showtime_id]
        ]
    if not available_seats:
        raise SystemExit(f"No remaining seats for showtime {showtime_id}")
    seat_window = max(1, len(available_seats) - seat_count + 1)
    start_index = (idx * 3) % seat_window
    picked_seats = available_seats[start_index:start_index + seat_count]
    if len(picked_seats) < seat_count:
        picked_seats = available_seats[:seat_count]
    for seat in picked_seats:
        used_seats_by_showtime[showtime_id].add(seat["seat_id"])

    showtime_start = parse_dt(start_text)
    showtime_end = parse_dt(end_text)
    booking_created_at = showtime_start - timedelta(hours=2)
    booking_reserved_until = booking_created_at + timedelta(minutes=15)
    payment_created_at = booking_created_at + timedelta(minutes=4)
    payment_paid_at = booking_created_at + timedelta(minutes=5)

    booking_id = uuid.uuid5(
        uuid.NAMESPACE_URL,
        f"seed-booking-{idx + 1}-{customer_id}-{showtime_id}",
    )
    payment_id = uuid.uuid5(uuid.NAMESPACE_URL, f"seed-payment-{booking_id}")

    ticket_subtotal = Decimal("0.00")
    seat_item_rows_for_booking = []
    seat_snapshot_payload = []
    for seat in picked_seats:
        seat_price = price_for_seat_type(seat["seat_type"], pricing)
        ticket_subtotal += seat_price
        seat_item_id = uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"seed-booking-seat-{booking_id}-{seat['seat_id']}",
        )
        seat_item_rows_for_booking.append(
            [
                sql_uuid(str(seat_item_id)),
                sql_uuid(str(booking_id)),
                sql_uuid(seat["seat_id"]),
                sql_text(seat["seat_code"]),
                sql_text(seat["seat_type"].upper()),
                sql_decimal(seat_price),
                sql_ts(booking_created_at),
            ]
        )
        seat_snapshot_payload.append(
            {
                "seatId": seat["seat_id"],
                "seatCode": seat["seat_code"],
                "seatType": seat["seat_type"].upper(),
                "seatPriceSnapshot": str(seat_price.quantize(Decimal("0.01"))),
            }
        )

    film_title = film_title_by_id.get(film_id)
    if film_title is None:
        raise SystemExit(f"Missing film title for film {film_id}")

    order_invoice_number = f"SEED-{showtime_start.date().strftime('%Y%m%d')}-{showtime_id[:8]}-{idx + 1:03d}"
    provider_ref = f"SEED-PROVIDER-{showtime_start.date().strftime('%Y%m%d')}-{idx + 1:03d}"
    pay_url = f"https://pay.cinemastar.local/checkout/{order_invoice_number}"
    webhook_event_key = f"SEED-WEBHOOK-{showtime_start.date().strftime('%Y%m%d')}-{showtime_id[:8]}-{idx + 1:03d}"
    applied_promotion = None
    promotion_discount_amount = Decimal("0.00")
    promotion_code = ""
    promotion_name = ""
    promotion_id = None
    if idx < 50:
        promotion_code_choice = "SEED-DISCOUNT-01"
        applied_promotion = promotion_by_code[promotion_code_choice]
        promotion_id = applied_promotion["id"]
        promotion_code = applied_promotion["code"]
        promotion_name = applied_promotion["name"]
        if applied_promotion["discount_type"] == "PERCENT":
            promotion_discount_amount = (
                ticket_subtotal
                * applied_promotion["discount_value"]
                / Decimal("100")
            )
            promotion_discount_amount = promotion_discount_amount.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
            max_discount = applied_promotion["max_discount_amount"]
            if max_discount is not None and promotion_discount_amount > max_discount:
                promotion_discount_amount = max_discount
        else:
            promotion_discount_amount = applied_promotion["discount_value"]
        if promotion_discount_amount > ticket_subtotal:
            promotion_discount_amount = ticket_subtotal
        promotion_discount_amount = promotion_discount_amount.quantize(Decimal("0.01"))
    payable_amount = (ticket_subtotal - promotion_discount_amount).quantize(Decimal("0.01"))
    if payable_amount < Decimal("0.00"):
        payable_amount = Decimal("0.00")
    checkout_payload = {
        "bookingId": str(booking_id),
        "showtimeId": showtime_id,
        "showtimeStartDateTime": showtime_start.strftime("%Y-%m-%dT%H:%M:%S"),
        "showtimeEndDateTime": showtime_end.strftime("%Y-%m-%dT%H:%M:%S"),
        "cinemaId": cinema_id,
        "filmId": film_id,
        "filmTitle": film_title,
        "userId": customer_id,
        "customerFullName": customer_name,
        "customerEmail": customer_email,
        "customerPhone": customer_phone,
        "amount": str(payable_amount),
        "grossAmount": str(ticket_subtotal.quantize(Decimal("0.01"))),
        "ticketSubtotalSnapshot": str(ticket_subtotal.quantize(Decimal("0.01"))),
        "productSubtotalSnapshot": "0.00",
        "promotionCode": promotion_code,
        "promotionName": promotion_name,
        "promotionDiscountAmount": str(promotion_discount_amount),
        "payableAmount": str(payable_amount),
        "currency": "VND",
        "paymentMethod": "MOMO_QR",
        "orderInvoiceNumber": order_invoice_number,
        "providerRef": provider_ref,
        "payUrl": pay_url,
        "seatSnapshots": seat_snapshot_payload,
        "expiresAt": booking_reserved_until.strftime("%Y-%m-%dT%H:%M:%S"),
    }
    response_payload = {
        "bookingId": str(booking_id),
        "paymentId": str(payment_id),
        "status": "PENDING",
        "amount": str(payable_amount),
        "orderInvoiceNumber": order_invoice_number,
        "providerRef": provider_ref,
        "webhookEventKey": webhook_event_key,
        "promotionCode": promotion_code,
        "promotionName": promotion_name,
        "promotionDiscountAmount": str(promotion_discount_amount),
    }

    booking_insert_rows.append(
        [
            sql_uuid(str(booking_id)),
            sql_uuid(showtime_id),
            sql_uuid(cinema_id),
            sql_uuid(film_id),
            sql_text(film_title),
            sql_ts(showtime_start),
            sql_ts(showtime_end),
            sql_uuid(customer_id),
            sql_text(customer_name),
            sql_text(customer_email),
            sql_text(customer_phone),
            "'UNPAID'",
            "'RESERVED'",
            sql_decimal(ticket_subtotal),
            sql_decimal(Decimal("0.00")),
            sql_decimal(ticket_subtotal),
            "NULL" if promotion_id is None else sql_uuid(str(promotion_id)),
            sql_text(promotion_code),
            sql_text(promotion_name),
            sql_decimal(promotion_discount_amount),
            sql_decimal(payable_amount),
            "false",
            sql_ts(booking_reserved_until),
            sql_ts(booking_created_at),
            sql_ts(booking_created_at),
        ]
    )

    booking_seat_insert_rows.extend(seat_item_rows_for_booking)

    payment_insert_rows.append(
        [
            sql_uuid(str(payment_id)),
            sql_uuid(str(booking_id)),
            sql_uuid(showtime_id),
            sql_uuid(cinema_id),
            sql_uuid(film_id),
            sql_uuid(customer_id),
            sql_decimal(payable_amount),
            sql_decimal(ticket_subtotal),
            sql_decimal(Decimal("0.00")),
            sql_text("VND"),
            sql_text("MOMO_QR"),
            sql_text(order_invoice_number),
            sql_text(provider_ref),
            sql_text(pay_url),
            sql_text(json.dumps(checkout_payload, ensure_ascii=False, separators=(",", ":"))),
            sql_text(json.dumps(response_payload, ensure_ascii=False, separators=(",", ":"))),
            "'PENDING'",
            sql_ts(booking_reserved_until),
            "NULL",
            "NULL",
            "NULL",
            "NULL",
            "NULL",
            "NULL",
            "''",
            "''",
            sql_decimal(Decimal("0.00")),
            sql_text(webhook_event_key),
            "NULL",
            sql_ts(payment_created_at),
            sql_ts(payment_created_at),
        ]
    )

    if applied_promotion is not None:
        payment_promotion_id = uuid.uuid5(uuid.NAMESPACE_URL, f"seed-payment-promotion-{payment_id}-{promotion_code}")
        payment_promotion_insert_rows.append(
            [
                sql_uuid(str(payment_promotion_id)),
                sql_uuid(str(payment_id)),
                sql_uuid(str(promotion_id)),
                sql_text(promotion_code),
                sql_text(promotion_name),
                sql_decimal(promotion_discount_amount),
                "1",
                sql_ts(payment_created_at),
                sql_ts(payment_created_at),
            ]
        )

    booking_final_updates.append((str(booking_id), payment_paid_at))
    payment_final_updates.append((str(payment_id), payment_paid_at))

booking_seed_sql = (
    "BEGIN;\n"
    + render_upsert(
        "booking",
        [
            "id",
            "showtime_id",
            "cinema_id",
            "film_id",
            "film_title",
            "showtime_start_date_time",
            "showtime_end_date_time",
            "user_id",
            "customer_full_name",
            "customer_email",
            "customer_phone",
            "payment_status",
            "booking_status",
            "ticket_subtotal",
            "product_subtotal",
            "final_amount",
            "promotion_id",
            "promotion_code",
            "promotion_name",
            "promotion_discount_amount",
            "payable_amount",
            "is_deleted",
            "reserved_until",
            "time_created",
            "time_updated",
        ],
        booking_insert_rows,
    )
    + render_upsert(
        "booking_seat_item",
        [
            "id",
            "booking_id",
            "seat_id",
            "seat_code",
            "seat_type",
            "seat_price_snapshot",
            "time_created",
        ],
        booking_seat_insert_rows,
    )
    + "COMMIT;\n"
)

promotion_seed_sql = (
    "BEGIN;\n"
    + render_upsert(
        "promotion",
        [
            "id",
            "code",
            "name",
            "description",
            "discount_type",
            "discount_value",
            "min_order_amount",
            "max_discount_amount",
            "status",
            "start_at",
            "end_at",
            "created_by_user_id",
            "created_by_role",
            "is_deleted",
            "time_created",
            "time_updated",
        ],
        promotion_seed_rows,
    )
    + "COMMIT;\n"
)

payment_seed_sql = (
    "BEGIN;\n"
    + render_upsert(
        "payment_transaction",
        [
            "id",
            "booking_id",
            "showtime_id",
            "cinema_id",
            "film_id",
            "user_id",
            "amount",
            "ticket_subtotal_snapshot",
            "product_subtotal_snapshot",
            "currency",
            "payment_method",
            "order_invoice_number",
            "provider_ref",
            "checkout_url",
            "checkout_payload_json",
            "response_payload_json",
            "status",
            "expires_at",
            "paid_at",
            "expired_at",
            "failure_reason",
            "refund_amount",
            "refund_reason",
            "refunded_at",
            "promotion_code",
            "promotion_name",
            "promotion_discount_amount",
            "webhook_event_key",
            "last_webhook_at",
            "time_created",
            "time_updated",
        ],
        payment_insert_rows,
    )
    + "COMMIT;\n"
)

payment_promotion_seed_sql = (
    "BEGIN;\n"
    + render_upsert(
        "payment_transaction_promotion",
        [
            "id",
            "payment_transaction_id",
            "promotion_id",
            "promotion_code",
            "promotion_name",
            "discount_amount",
            "apply_order",
            "time_created",
            "time_updated",
        ],
        payment_promotion_insert_rows,
    )
    + "COMMIT;\n"
)

booking_finalize_sql = (
    "BEGIN;\n"
    + render_values_update(
        "booking",
        booking_final_updates,
        "booking_status = 'CONFIRMED', payment_status = 'PAID', time_updated = v.event_at",
    )
    + "COMMIT;\n"
)

payment_finalize_sql = (
    "BEGIN;\n"
    + render_values_update(
        "payment_transaction",
        payment_final_updates,
        "status = 'PAID', paid_at = v.event_at, last_webhook_at = v.event_at, response_payload_json = '{\"resultCode\":0,\"message\":\"Success\"}', time_updated = v.event_at",
    )
    + "COMMIT;\n"
)

(tmp_dir / "booking_seed.sql").write_text(booking_seed_sql, encoding="utf-8")
(tmp_dir / "promotion_seed.sql").write_text(promotion_seed_sql, encoding="utf-8")
(tmp_dir / "payment_seed.sql").write_text(payment_seed_sql, encoding="utf-8")
(tmp_dir / "payment_promotion_seed.sql").write_text(payment_promotion_seed_sql, encoding="utf-8")
(tmp_dir / "booking_finalize.sql").write_text(booking_finalize_sql, encoding="utf-8")
(tmp_dir / "payment_finalize.sql").write_text(payment_finalize_sql, encoding="utf-8")
PY

run_psql_file booking_db "${TMP_DIR}/booking_seed.sql"
run_psql_file payment_db "${TMP_DIR}/promotion_seed.sql"
run_psql_file payment_db "${TMP_DIR}/payment_seed.sql"
run_psql_file payment_db "${TMP_DIR}/payment_promotion_seed.sql"
run_psql_file payment_db "${TMP_DIR}/payment_finalize.sql"
run_psql_file booking_db "${TMP_DIR}/booking_finalize.sql"

echo "Imported 100 booking rows and 100 payment transactions."
