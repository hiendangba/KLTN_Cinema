CREATE USER identity_user WITH PASSWORD 'identity_pass';
CREATE USER user_user WITH PASSWORD 'user_pass';
CREATE USER film_user WITH PASSWORD 'film_pass';
CREATE USER showtime_user WITH PASSWORD 'showtime_pass';
CREATE USER hall_user WITH PASSWORD 'hall_pass';
CREATE USER cinema_user WITH PASSWORD 'cinema_pass';
CREATE USER booking_user WITH PASSWORD 'booking_pass';

CREATE DATABASE identity_db OWNER identity_user;
CREATE DATABASE user_db OWNER user_user;
CREATE DATABASE film_db OWNER film_user;
CREATE DATABASE showtime_db OWNER showtime_user;
CREATE DATABASE hall_db OWNER hall_user;
CREATE DATABASE cinema_db OWNER cinema_user;
CREATE DATABASE booking_db OWNER booking_user;

REVOKE ALL ON DATABASE identity_db FROM PUBLIC;
REVOKE ALL ON DATABASE user_db FROM PUBLIC;
REVOKE ALL ON DATABASE film_db FROM PUBLIC;
REVOKE ALL ON DATABASE showtime_db FROM PUBLIC;
REVOKE ALL ON DATABASE hall_db FROM PUBLIC;
REVOKE ALL ON DATABASE cinema_db FROM PUBLIC;
REVOKE ALL ON DATABASE booking_db FROM PUBLIC;

GRANT ALL PRIVILEGES ON DATABASE identity_db TO identity_user;
GRANT ALL PRIVILEGES ON DATABASE user_db TO user_user;
GRANT ALL PRIVILEGES ON DATABASE film_db TO film_user;
GRANT ALL PRIVILEGES ON DATABASE showtime_db TO showtime_user;
GRANT ALL PRIVILEGES ON DATABASE hall_db TO hall_user;
GRANT ALL PRIVILEGES ON DATABASE cinema_db TO cinema_user;
GRANT ALL PRIVILEGES ON DATABASE booking_db TO booking_user;
