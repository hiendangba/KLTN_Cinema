---
name: showtime-response-contract
description: Maintain and refactor Showtime API response contracts in the CinemaStar microservices codebase. Use when removing/replacing response DTOs (for example ShowTimeWithFilmResponse), aligning controller-service signatures, updating mapping logic, deleting dead DTO files, and syncing README/API docs without breaking runtime behavior.
---

# Showtime Response Contract Skill

Keep API contract changes consistent across `showtime-service`.

## Follow This Workflow

1. Locate all references of the target DTO/type.
2. Update service interface signatures first.
3. Update controller signatures and imports to match service signatures.
4. Update service implementation return types and mapping logic.
5. Remove obsolete DTO files only after reference search returns empty.
6. Re-scan repository for stale imports, endpoint paths, and old method names.
7. Update `README.md` endpoint/docs/changelog sections to reflect the new contract.

## Search Commands

Use fast scans before and after edits:

```powershell
rg -n "ShowTimeWithFilmResponse|search-with-film|with-film" -S showtime-service README.md
rg -n "searchShowtimes|getShowTimeById" -S showtime-service/src/main/java
```

## Contract Rules

- Keep `ShowTimeResponse` as the canonical response DTO.
- Keep foreign-key IDs in `ShowTimeResponse` (`hallId`, `filmId`, `pricingPolicyId`) even if nested objects are present.
- Enrich response with nested objects (`film`, `hall`, `pricingPolicy`) in service layer.
- Avoid introducing parallel response DTOs unless there is a clear separate API contract.

## Safety Checks

- Ensure old endpoints are removed from code and docs together.
- Ensure no compile-time references remain to deleted DTOs.
- Do not revert unrelated local changes in a dirty workspace.
- Always append/update a short changelog note in `README.md` after each completed task.

## Definition Of Done

- `rg` search shows no old DTO references.
- Controller and service method signatures are aligned.
- DTO deletion is complete and imports are clean.
- README reflects current endpoints and response format.
- README includes a completion note/changelog entry for the work just finished.
