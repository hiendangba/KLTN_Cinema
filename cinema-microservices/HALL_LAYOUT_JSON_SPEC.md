# Hall Layout JSON Spec (Final)

This file is the final contract for `layoutJson` in `hall-services`.
Use it as the source of truth for FE Hall Layout Editor implementation.

## 1) Goal

- Store hall seat layout in one JSON field: `layoutJson`.
- Keep payload compact: store only drawn blocks.
- Enforce strict backend validation to prevent duplicate/overlapped seats.

## 2) Data Contract

`layoutJson` contains only `items`:

```json
{
  "items": [
    {
      "id": "A1",
      "type": "SEAT",
      "seatType": "STANDARD",
      "row": 1,
      "col": 1,
      "rowspan": 1,
      "colspan": 1,
      "seatCount": 1
    },
    {
      "id": "CP1",
      "type": "SEAT",
      "seatType": "COUPLE",
      "row": 5,
      "col": 10,
      "rowspan": 1,
      "colspan": 2,
      "seatCount": 2
    },
    {
      "id": "W1",
      "type": "AISLE",
      "seatType": null,
      "row": 1,
      "col": 5,
      "rowspan": 1,
      "colspan": 1,
      "seatCount": 0
    }
  ]
}
```

## 3) Required Rules

- Every item must have all fields:
  - `id`, `type`, `seatType`, `row`, `col`, `rowspan`, `colspan`, `seatCount`
- Coordinate rules:
  - `row >= 1`, `col >= 1`
  - `rowspan >= 1`, `colspan >= 1`
- Rule by type:
  - `SEAT`:
    - `seatType` is required (`STANDARD | VIP | COUPLE | ...`)
    - `seatCount >= 1`
  - `AISLE`:
    - `seatType = null`
    - `seatCount = 0`
- Fixed rule for `COUPLE`:
  - Horizontal only
  - `rowspan = 1`
  - `colspan = 2`
  - `seatCount = 2`

## 4) Backend Validation (Mandatory)

- `id` in `items` must be unique.
- No overlap is allowed across all item types (`SEAT` and `AISLE`).
- Overlap check algorithm:
  - Expand each item to occupied cells:
    - `r in [row .. row + rowspan - 1]`
    - `c in [col .. col + colspan - 1]`
  - Build key `r:c` and store in a `Set`.
  - If key already exists -> reject with `400 Bad Request` and return duplicated cells.
- Hall `capacity` must be calculated automatically:
  - `capacity = sum(seatCount)` for all `type = SEAT`.

## 5) FE Prompt Template

```text
Build Hall Layout Editor for cinema seating.

Use this exact JSON contract for layoutJson:
{
  "items": [
    {
      "id": "A1",
      "type": "SEAT",
      "seatType": "STANDARD",
      "row": 1,
      "col": 1,
      "rowspan": 1,
      "colspan": 1,
      "seatCount": 1
    },
    {
      "id": "CP1",
      "type": "SEAT",
      "seatType": "COUPLE",
      "row": 5,
      "col": 10,
      "rowspan": 1,
      "colspan": 2,
      "seatCount": 2
    },
    {
      "id": "W1",
      "type": "AISLE",
      "seatType": null,
      "row": 1,
      "col": 5,
      "rowspan": 1,
      "colspan": 1,
      "seatCount": 0
    }
  ]
}

Editor requirements:
- Allow draw/move/resize/delete items on a grid canvas.
- Keep all item fields consistent: id, type, seatType, row, col, rowspan, colspan, seatCount.
- Enforce AISLE => seatType=null and seatCount=0.
- Enforce SEAT => seatType required and seatCount>=1.
- Enforce COUPLE => rowspan=1, colspan=2, seatCount=2.
- Validate overlap before save (no duplicate occupied cells).
- Show computed hall capacity = sum(seatCount) for SEAT items.
- Export exactly this JSON shape for backend PATCH /api/halls/{id}/layout.
```

