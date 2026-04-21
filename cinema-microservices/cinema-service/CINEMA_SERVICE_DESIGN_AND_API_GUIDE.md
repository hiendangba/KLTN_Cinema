# Cinema Service Design and API Guide

## 1) Entity model inferred from your class diagram

Based on your diagram and the microservice boundary, the implementation uses 2 entities:

### `Cinema`
- `id: UUID`
- `code: String` (unique)
- `name: String`
- `address: String`
- `latitude: Decimal(9,6)`
- `longitude: Decimal(9,6)`
- `phone: String`
- `openTime: Time`
- `closeTime: Time`
- `status: CinemaStatus` (`ACTIVE`, `INACTIVE`, `MAINTENANCE`)
- `managerId: UUID`
- `isDeleted: Boolean`
- `createdAt: LocalDateTime`
- `updatedAt: LocalDateTime`

### `CinemaStaff` (mapping table)
- `id: UUID`
- `cinemaId: UUID`
- `staffId: UUID`
- `active: Boolean`
- `createdAt: LocalDateTime`
- `updatedAt: LocalDateTime`

`staffId` is unique in this table to enforce your business rule: one staff belongs to only one cinema.

## 2) Why `staffIds: list<UUID>` was not stored directly in `Cinema`

Even though the diagram shows `staffIds: list<UUID>`, this was normalized into `CinemaStaff` table because:
1. Query and filtering are much easier and faster in SQL.
2. It enforces integrity with a unique constraint for one-staff-one-cinema.
3. It avoids denormalized list parsing in database.
4. It keeps microservice boundaries clean (cinema keeps references, not full user data).

The API response still returns `staffIds` in `CinemaResponse`, so frontend behavior remains simple.

## 3) Implemented APIs

Base path: `/api/cinemas`

### Core cinema APIs
1. `POST /api/cinemas`
- Create cinema (admin role)

2. `GET /api/cinemas/{id}`
- Get cinema detail by id

3. `POST /api/cinemas/search`
- Cursor-based search with keyword/sort/filter

4. `PUT /api/cinemas/{id}`
- Update cinema profile (admin role)

5. `PATCH /api/cinemas/{id}`
- Update cinema status (admin role)

6. `DELETE /api/cinemas/{id}`
- Soft delete cinema, and deactivate assigned staffs (admin role)

### Staff mapping APIs
7. `POST /api/cinemas/{id}/staffs`
- Assign one staff to cinema (admin role)

8. `DELETE /api/cinemas/{id}/staffs/{staffId}`
- Unassign staff from cinema (admin role)

9. `GET /api/cinemas/{id}/staffs`
- List staff assignments of cinema

### Manager convenience API
10. `GET /api/cinemas/me`
- Get managed cinema by `X-User-ID` + manager role

## 4) Implemented internal gRPC (for other services)

`CinemaInternalService` methods implemented in cinema-service:
1. `GetCinemaByUserId`
- Input: manager/user id
- Output: cinema payload (id, name)

2. `GetCinemaById`
- Input: cinema id
- Output: cinema payload (id, name)

This is compatible with current `hall-service` gRPC client usage.

## 5) Validation and business rules applied

1. `latitude` range: `[-90, 90]`
2. `longitude` range: `[-180, 180]`
3. `openTime < closeTime`
4. `code` is normalized uppercase and must be unique among non-deleted cinemas
5. `managerId` (if provided) cannot be assigned to another non-deleted cinema
6. one staff can only belong to one cinema (`UNIQUE(staff_id)`)
7. delete cinema is soft-delete (`isDeleted = true`)

## 6) Authorization policy used

Headers expected:
- `X-User-ID`
- `X-User-Role`

Role checks:
- Admin required for create/update/delete and staff assignment APIs.
- Manager required for `/api/cinemas/me`.

## 7) Files created/updated

### New implementation files
- `cinema-service/src/main/resources/application.yaml`
- `cinema-service/src/main/java/com/cinema/cinema_service/enums/CinemaStatus.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/entity/Cinema.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/entity/CinemaStaff.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/dto/request/CreateCinemaRequest.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/dto/request/UpdateCinemaRequest.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/dto/request/UpdateCinemaStatusRequest.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/dto/request/AssignCinemaStaffRequest.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/dto/request/CinemaField.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/dto/response/CinemaResponse.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/dto/response/CinemaStaffResponse.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/mapper/CinemaMapper.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/repository/CinemaRepository.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/repository/CinemaStaffRepository.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/repository/CinemaRepositoryImpl.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/services/CinemaService.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/services/impl/CinemaServiceImpl.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/controller/CinemaController.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/grpc/CinemaInternalGrpcService.java`

### Updated files
- `cinema-service/pom.xml`
- removed `cinema-service/src/main/resources/application.properties`

## 8) Build verification

Verified in local workspace:
- `cinema-service`: `./mvnw.cmd -q -DskipTests compile` passed.

## 9) Skills used for this implementation

As requested, this work followed these loaded skills:
- `java-pro`
- `api-endpoint-builder`
- `backend-dev-guidelines`

The implementation reflects those skill goals:
- layered architecture
- validation-first endpoints
- consistent response format
- microservice-safe entity boundaries
- production-ready API surface
