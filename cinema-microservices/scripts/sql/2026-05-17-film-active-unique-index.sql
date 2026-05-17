-- Purpose:
-- Enforce uniqueness for active films only:
-- (title, release_date) must be unique when is_deleted = false.
--
-- Safe to run multiple times.

BEGIN;

-- Remove old table-level unique constraint/index if present.
ALTER TABLE films
    DROP CONSTRAINT IF EXISTS uk_film_title_release_date_isdeleted;

DROP INDEX IF EXISTS uk_film_title_release_date_isdeleted;
DROP INDEX IF EXISTS uk_film_title_release_date_active;

-- Guard: stop migration if active duplicate data already exists.
DO
$$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM films
            WHERE is_deleted = false
            GROUP BY title, release_date
            HAVING COUNT(*) > 1
        ) THEN
            RAISE EXCEPTION 'Active duplicate films detected for (title, release_date). Resolve duplicates before applying unique index.';
        END IF;
    END
$$;

-- Partial unique index: applies only to active records.
CREATE UNIQUE INDEX uk_film_title_release_date_active
    ON films (title, release_date)
    WHERE is_deleted = false;

COMMIT;
