# Sprint Progress

## Sprint 1 - Conditional acceptance

**Status:** Source implementation and automated tests are complete. Sprint 1 is **not yet accepted for SQL Server production use**: the reviewed database migration and grants have not been applied, real SQL Server flows have not been tested, and the proposed endpoint-role policy still needs owner confirmation.

### Completed

- Reviewed the `users.email` migration draft, including normalized duplicate/conflict preflight checks. At that review point it was not applied; current metadata alone does not establish migration provenance.
- Reviewed the separate Spring Boot and NestJS database-role draft. Spring is denied writes to `course_registrations`; Nest is denied writes to the Spring-owned account/profile tables. It is not applied.
- Reviewed current Spring and NestJS endpoint authentication and role enforcement; documented current behavior separately from the proposed access policy in [SPRINT_1_ACCEPTANCE.md](./SPRINT_1_ACCEPTANCE.md).
- Updated lecturer provisioning to require a linked, active account with an unrevoked `LECTURER` role and to synchronize account email within the profile transaction, including when an existing user ID is supplied.
- Added/updated automated checks for student and lecturer account/profile email synchronization and lecturer role linkage.
- Updated the email migration to reject ASCII and specified Unicode whitespace before backfill/index creation; it does not trim or replace whitespace. Added a read-only SQL Server preflight with real-data counts and synthetic test vectors.
- Finalized the migration rerun guard so it only treats an enabled, non-hypothetical, single-key unique filtered email index as already applied.
- Fixed SQL Server compile-time binding in the migration: the post-`ALTER TABLE` backfill and unique-index creation now run via `sp_executesql`, using session-local `#ProfileEmails`. This is a draft-only correction; it has not been executed.
- Updated login resolution to return a user only when exactly one distinct account matches; removed `TOP (1)` account selection while retaining username, email, student-code, and employee-code login.
- Added authentication tests for ambiguous identifiers, identifiers matching multiple fields on one account, and unique student/employee codes.
- Clean-compiled and reran focused authentication tests after the latest changes: **8 passed, 0 failed, 0 errors**.
- Reran the complete Spring test suite after the latest changes: **11 passed, 0 failed, 0 errors** (`mvn test`, H2 test database). SQL Server preflight vectors: **29 passed**; actual profile email values scanned: **0**.
- Previous NestJS verification: **3 Jest suites / 8 tests passed**, and production build passed. No NestJS source was changed in this acceptance review.
- On 2026-11-27, implemented the six catalog management tabs in the ADMIN portal: departments, majors, training programs, academic years, school years, and semesters. Added search, active/related filters, detail view, create/edit, and soft delete using the existing JWT-protected Spring CRUD APIs.
- Tightened catalog validation against current SQL Server keys and relationships, corrected year inputs to `smallint` values, removed the non-schema `schoolYearName` field, and aligned the legacy `academic.js` calls to include the portal access token.
- Added catalog API tests for ADMIN authorization, 401/403 responses, FK/parent consistency, year value mapping, date bounds, and scoped duplicate keys. Focused `AcademicManagementApiTests`: **3 passed, 0 failed, 0 errors** on H2; `node --check` passed for `portal.js`, `academic.js`, and `student.js`.
- Corrected the legacy student form mapping too: `admissionYear` is an integer; removed the unsupported `academicYearYear` input; its API calls now send the stored bearer token and show HTTP status with errors.
- The current catalog changes did not execute SQL writes, DDL, migrations, or permission changes.
- On 2026-11-27, completed the ADMIN student-class source flow: CRUD/detail/status endpoint, code/name search, department/major/cohort/status filters, pagination, related-name dropdowns, and major/program cascading filters. Backend validation matches the inspected SQL Server schema, prevents duplicate codes, preserves class-history constraints, blocks soft-delete when students/history exist, and serializes student assignment against class deactivation with a transactional row lock.
- Updated the student form class dropdown to use real API data: inactive classes are excluded for new students while an existing inactive class remains selectable during profile edits. Existing student/account/role/email transaction was retained.
- Completed ADMIN logout behavior: call Spring logout to revoke refresh token when possible, always clear browser tokens/account/role/UI state, and return to login even if server revocation fails. Access JWT remains stateless and is not immediately revoked by this client action.
- Added/updated H2 API tests for class CRUD, schema-length code, related lookups, duplicate code, class status, inactive-class assignment, class-history preservation and protected deletion; added logout checks for refresh-token revocation and protected API access without a token.
- Re-ran `mvnw clean test`: **13 passed, 0 failed, 0 errors** (H2). `node --check src/main/resources/static/assets/js/portal.js` and `git diff --check` passed. No browser E2E or live SQL Server test was run.
- No migration, DDL/DML, database grant, or SQL Server data change was performed for student-class/logout work. Updated [FUNCTIONAL_GAP_ANALYSIS.md](./FUNCTIONAL_GAP_ANALYSIS.md) with scope, test evidence, SQL Server limitation and a NestJS-only bulk-registration proposal.

### Files changed for student-class/logout

- Backend: `src/main/java/com/example/quanlydaotao/entity/StudentClass.java`; `controller/StudentClassController.java`; `controller/StudentFormOptionsController.java`; `repository/StudentClassRepository.java`; `repository/StudentRepository.java`; `repository/StudentClassHistoryRepository.java`; `service/StudentClassService.java`; `service/StudentService.java`.
- Frontend: `src/main/resources/static/index.html`; `src/main/resources/static/assets/css/portal.css`; `src/main/resources/static/assets/js/portal.js`.
- Tests and docs: `src/test/java/com/example/quanlydaotao/AcademicManagementApiTests.java`; `src/test/java/com/example/quanlydaotao/AuthJwtApiTests.java`; `FUNCTIONAL_GAP_ANALYSIS.md`; `SPRINT_PROGRESS.md`.
- No Spring/Nest database ownership change was made. No registration API, bulk operation, SQL migration or DB grant was added.

### Not completed / acceptance blockers

- A read-only SQL Server check on 2026-11-27 found `dbo.users.email` and enabled unique filtered index `UX_users_email` (`email IS NOT NULL`) present; `users` has 1 row, `students` and `employees` have 0 rows, and no user currently has a non-NULL email. This does not establish which migration/change applied the column or index; DBA migration history is still needed.
- The email migration draft checks the filtered unique-index predicate, runs preflight plus DDL in one SERIALIZABLE transaction, and rejects the specified ASCII/Unicode whitespace without normalizing it away. Its application/provenance was not established by the current read-only metadata check; post-commit rollback still requires a separate data-preserving plan.
- SQL Server reports `ONLINE`, recovery model `FULL`, compatibility level `160`; the connected instance has no `msdb` backup records for this database. An externally managed backup cannot be ruled out, so DBA confirmation and restore verification are required before applying.
- The catalog work made no database changes. The live status of the backend grants and migration history was not re-verified in this task.
- No end-to-end application flow has been accepted against SQL Server. H2 tests do not establish SQL Server compatibility or production readiness.
- The new student-class CRUD, class dropdown and logout flows have not been tested end-to-end against SQL Server. Pessimistic locking uses the JPA lock mechanism but still requires SQL Server QA verification under the configured Hibernate dialect.
- The live SQL Server currently has `users.email` and its filtered unique index, but no student/employee rows. The migration provenance and the new login query have not been verified against populated SQL Server data.
- The live database has **zero grading-scale rows**. A usable active scale must be prepared before a course section can be opened.
- The proposed endpoint-role matrix is not an authorization change. Obtain explicit confirmation before broadening access beyond current enforcement.

### Required before SQL Server acceptance

1. Ask the DBA to confirm the migration history for `users.email`/`UX_users_email`; review preflight findings and apply the migration only if the approved database process confirms it is still required.
2. Create separate, least-privileged SQL Server users for Spring Boot and NestJS; review and apply the database-role script with the actual database-user names.
3. Configure each backend with its own database credentials and the required shared JWT configuration. Keep secrets in environment/secret storage, not in source control or these documents.
4. Ensure a usable ADMIN account exists, then execute the SQL Server checklist in [SPRINT_1_ACCEPTANCE.md](./SPRINT_1_ACCEPTANCE.md) against a QA database. Include student-class CRUD/status and history guards, student create/update with active/inactive class dropdown behavior, logout refresh-token revocation, and post-logout protected requests.
5. Prepare and verify an active grading scale using the project’s approved data-management process.
6. Confirm the proposed endpoint-role policy before changing API authorization.

### Sprint 2 boundary

Sprint 2 has **not** started. The student-class and logout source work in this update remains subject to SQL Server QA and the Sprint 1 acceptance blockers. Only after the Sprint 1 blockers are resolved and the role policy is confirmed may Sprint 2 be planned and authorized. This progress update does not authorize or implement Sprint 2 work.
