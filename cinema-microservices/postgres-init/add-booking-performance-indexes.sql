CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_booking_report_showtime
    ON booking (is_deleted, booking_status, showtime_start_date_time);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_booking_report_cinema_film
    ON booking (is_deleted, cinema_id, film_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_booking_user_active
    ON booking (user_id, is_deleted, booking_status, reserved_until);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_booking_showtime_status
    ON booking (showtime_id, is_deleted, booking_status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_booking_seat_item_booking_id
    ON booking_seat_item (booking_id);
