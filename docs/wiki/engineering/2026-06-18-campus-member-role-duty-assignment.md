# 2026-06-18 Campus Member Role And Coffee Duty Assignment

## Scope

- Issue: #30 `[Feat] 캠퍼스 멤버 역할과 커피 담당자 관리`
- Branch: `feat/30-campus-member-role-duty-assignment`
- APIs implemented:
  - `GET /api/v1/admin/campuses/{campusId}/members`
  - `PATCH /api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role`
  - `GET /api/v1/admin/campuses/{campusId}/duty-assignments`
  - `PUT /api/v1/admin/campuses/{campusId}/duty-assignments/coffee`
  - `DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}`

## Decisions Recorded

- Campus role change uses same-level assignment semantics: a campus manager can assign up to their own campus role level, but cannot change or assign roles above that level.
- `ADMIN` can change all campus roles.
- Service-level `MANAGER` alone does not grant campus role change or coffee duty management permission.
- Coffee duty management is allowed for `ADMIN` and active campus members whose campus role is not `MEMBER`.

## Implementation Notes

- Added `CampusDutyAssignment` and `DutyType.COFFEE`.
- Coffee duty is stored separately from `CampusRole`.
- `CampusRole` owns the hierarchy comparison logic.
- `CampusService` coordinates membership lookup, permission checks, role updates, and active coffee assignment replacement.
- Controllers return DTOs only; no Entity is returned from Controller methods.
- Swagger documentation annotations were not added.

## Verification

- TDD failure check: new tests failed at `compileTestJava` before implementation because `DutyType`, role/duty commands, duty result, and service methods did not exist.
- `./gradlew test`: success, 46 tests / 0 failures / 0 errors / 0 skipped.
- `./gradlew build`: success.
- `./gradlew asciidoctor`: success.
- Spring REST Docs snippet groups: 22.
- Admin campus snippet groups added: 5.
- Forbidden-term scan: no source/test/API-doc violations for the configured forbidden terms or single `optionId` request field.

## Evidence

- API tests: `CampusControllerTest`
- Application tests: `CampusServiceTest`
- REST Docs tests: `CampusApiRestDocsTest`
- API docs index: `src/docs/asciidoc/index.adoc`
