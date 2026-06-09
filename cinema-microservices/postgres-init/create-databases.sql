DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'identity_user') THEN
        CREATE ROLE identity_user LOGIN PASSWORD 'identity_pass';
    ELSE
        ALTER ROLE identity_user WITH LOGIN PASSWORD 'identity_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_user') THEN
        CREATE ROLE user_user LOGIN PASSWORD 'user_pass';
    ELSE
        ALTER ROLE user_user WITH LOGIN PASSWORD 'user_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'film_user') THEN
        CREATE ROLE film_user LOGIN PASSWORD 'film_pass';
    ELSE
        ALTER ROLE film_user WITH LOGIN PASSWORD 'film_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'showtime_user') THEN
        CREATE ROLE showtime_user LOGIN PASSWORD 'showtime_pass';
    ELSE
        ALTER ROLE showtime_user WITH LOGIN PASSWORD 'showtime_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hall_user') THEN
        CREATE ROLE hall_user LOGIN PASSWORD 'hall_pass';
    ELSE
        ALTER ROLE hall_user WITH LOGIN PASSWORD 'hall_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cinema_user') THEN
        CREATE ROLE cinema_user LOGIN PASSWORD 'cinema_pass';
    ELSE
        ALTER ROLE cinema_user WITH LOGIN PASSWORD 'cinema_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'booking_user') THEN
        CREATE ROLE booking_user LOGIN PASSWORD 'booking_pass';
    ELSE
        ALTER ROLE booking_user WITH LOGIN PASSWORD 'booking_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_user') THEN
        CREATE ROLE payment_user LOGIN PASSWORD 'payment_pass';
    ELSE
        ALTER ROLE payment_user WITH LOGIN PASSWORD 'payment_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'seat_user') THEN
        CREATE ROLE seat_user LOGIN PASSWORD 'seat_pass';
    ELSE
        ALTER ROLE seat_user WITH LOGIN PASSWORD 'seat_pass';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'upload_user') THEN
        CREATE ROLE upload_user LOGIN PASSWORD 'upload_pass';
    ELSE
        ALTER ROLE upload_user WITH LOGIN PASSWORD 'upload_pass';
    END IF;
END
$$;

SELECT format('CREATE DATABASE %I OWNER %I', 'identity_db', 'identity_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'identity_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'user_db', 'user_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'user_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'film_db', 'film_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'film_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'showtime_db', 'showtime_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'showtime_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'hall_db', 'hall_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'hall_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'cinema_db', 'cinema_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'cinema_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'booking_db', 'booking_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'booking_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'payment_db', 'payment_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'payment_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'seat_db', 'seat_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'seat_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'upload_db', 'upload_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'upload_db') \gexec

ALTER DATABASE identity_db OWNER TO identity_user;
ALTER DATABASE user_db OWNER TO user_user;
ALTER DATABASE film_db OWNER TO film_user;
ALTER DATABASE showtime_db OWNER TO showtime_user;
ALTER DATABASE hall_db OWNER TO hall_user;
ALTER DATABASE cinema_db OWNER TO cinema_user;
ALTER DATABASE booking_db OWNER TO booking_user;
ALTER DATABASE payment_db OWNER TO payment_user;
ALTER DATABASE seat_db OWNER TO seat_user;
ALTER DATABASE upload_db OWNER TO upload_user;

REVOKE ALL ON DATABASE identity_db FROM PUBLIC;
REVOKE ALL ON DATABASE user_db FROM PUBLIC;
REVOKE ALL ON DATABASE film_db FROM PUBLIC;
REVOKE ALL ON DATABASE showtime_db FROM PUBLIC;
REVOKE ALL ON DATABASE hall_db FROM PUBLIC;
REVOKE ALL ON DATABASE cinema_db FROM PUBLIC;
REVOKE ALL ON DATABASE booking_db FROM PUBLIC;
REVOKE ALL ON DATABASE payment_db FROM PUBLIC;
REVOKE ALL ON DATABASE seat_db FROM PUBLIC;
REVOKE ALL ON DATABASE upload_db FROM PUBLIC;

GRANT ALL PRIVILEGES ON DATABASE identity_db TO identity_user;
GRANT ALL PRIVILEGES ON DATABASE user_db TO user_user;
GRANT ALL PRIVILEGES ON DATABASE film_db TO film_user;
GRANT ALL PRIVILEGES ON DATABASE showtime_db TO showtime_user;
GRANT ALL PRIVILEGES ON DATABASE hall_db TO hall_user;
GRANT ALL PRIVILEGES ON DATABASE cinema_db TO cinema_user;
GRANT ALL PRIVILEGES ON DATABASE booking_db TO booking_user;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO payment_user;
GRANT ALL PRIVILEGES ON DATABASE seat_db TO seat_user;
GRANT ALL PRIVILEGES ON DATABASE upload_db TO upload_user;
