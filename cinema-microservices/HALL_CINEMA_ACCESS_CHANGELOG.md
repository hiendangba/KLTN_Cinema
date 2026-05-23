# Hall and Cinema Access Changes

Date: 2026-05-24

## What changed

### Hall service
- Simplified `deleteHall` to use only `hallId`.
- Delete flow now loads the hall first, derives `cinemaId` from the hall, and validates ownership server-side.
- `getHallById` is now role-scoped:
  - `ADMIN` can read any hall.
  - `MANAGER` can read only halls that belong to cinemas they own.
  - `STAFF` can read only halls that belong to the cinema they are assigned to.
- `searchHalls` is now role-scoped:
  - `ADMIN` gets full results.
  - `MANAGER` gets only halls from owned cinemas.
  - `STAFF` gets only halls from the cinema they are assigned to.
- Hall response enrichment still happens after authorization checks.
- Hall cache lookup for `getHallById` is still supported, but now it runs after access validation.

### Cinema service
- Removed the old 1-manager-1-cinema assumption.
- `GET /api/cinemas/me` now returns a list of cinemas for the authenticated manager.
- `getCinemaById` is now role-scoped:
  - `ADMIN` can read any cinema.
  - `MANAGER` can read only their own cinemas.
  - `STAFF` can read only the cinema they are assigned to.
- `searchCinemas` is still the single public search API, but it now applies role scoping internally:
  - `ADMIN` gets full results.
  - `MANAGER` gets only cinemas owned by the current manager.
  - `STAFF` gets only the cinema they are assigned to.
- `GET /api/cinemas/{id}/staffs` now also checks role and ownership before returning data.
- The extra role-aware lookup method in service code is an internal helper for `GET /me` and internal gRPC calls, not a new public REST API.

## API notes

- Hall delete no longer needs `cinemaId` from the client.
- Ownership is derived from `hall.cinemaId -> cinema.managerId`.
- If a cinema changes manager, the hall ownership view changes automatically on the next request.

## Internal contract changes

- Added a list-based internal cinema lookup for manager ownership:
  - `GetCinemasByUserId`
  - `GetCinemasByUserIdReply`
- Existing single-cinema lookup remains for backward-compatible reads where needed.

## Verification notes

- I verified the service code paths and test coverage for the new ownership rules.
- Full Maven verification in this environment was blocked by an existing `common-lib` generated-source issue unrelated to the hall/cinema logic changes.

## Key files touched

- `cinema-service/src/main/java/com/cinema/cinema_service/services/impl/CinemaServiceImpl.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/controller/CinemaController.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/services/CinemaService.java`
- `hall-service/src/main/java/com/cinema/hall_service/services/impl/HallServiceImpl.java`
- `hall-service/src/main/java/com/cinema/hall_service/controller/HallController.java`
- `hall-service/src/main/java/com/cinema/hall_service/services/HallService.java`
- `common-lib/src/main/proto/cinema_internal.proto`
