# Sprint 1 Acceptance

## Verdict

**Conditional / not accepted for SQL Server operation yet.** The source changes and automated Spring tests pass, but SQL Server acceptance remains blocked on the un-applied migration and grants, real-database testing, grading-scale data, and confirmation of the proposed endpoint-role policy.

No schema or data-changing SQL was run. No migration or database permission script was applied. No Sprint 2 implementation was started.

## 1. Work completed

- Reviewed the current email migration and separate database-role script without executing either.
- Fresh read-only SQL Server checks confirmed `dbo.users.email` is absent, `users` has 1 row, and `students`/`employees` have 0 rows each. Both profile email columns have 0 non-NULL values, so current duplicate/conflict/whitespace checks return zero on empty profile data rather than proving populated-data readiness. No orphan profile-user links or unlinked employee emails were found. The live database has no grading-scale rows.
- The database is `ONLINE`, recovery model `FULL`, compatibility level `160`. No backup history was found in the connected instance's `msdb`; external backups may exist, but backup currency/restorability is unverified.
- Reviewed existing endpoint enforcement across Spring Boot and NestJS.
- Tightened the unexecuted email migration draft: exact filtered-index predicate validation, serializable transaction covering data preflight/backfill/index creation, and strict rejection of listed ASCII/Unicode whitespace before backfill. Whitespace is detected in the source value and is never trimmed or replaced by the migration.
- Added `database/preflight-v2-global-account-email.sql`, a read-only real-data preflight plus SQL Server test vectors for the whitespace detector.
- Changed login resolution to reject zero or multiple matching accounts rather than selecting an arbitrary row; added tests for collision, unique profile-code login, and one account matching multiple identifier fields.
- Updated lecturer profile creation/update so its linked account must be active and have an active, unrevoked `LECTURER` role; the account email is synchronized transactionally whether the account is newly provisioned or already exists.
- Added/updated tests for student and lecturer account/profile email synchronization and lecturer role assignment.
- After the latest changes, `mvn clean -Dtest=AuthJwtApiTests test`: **8 passed, 0 failed, 0 errors**. The test class includes normal username/email authentication, ambiguous identifier rejection, unique student/employee-code authentication, and a single account matching multiple identifier fields.
- Full Spring suite after the latest changes, `mvn test`: **11 passed, 0 failed, 0 errors**. These automated tests use H2, not SQL Server.
- The read-only SQL Server whitespace preflight returned 0 real profile email values and 0 current matches. Its **29 synthetic vectors passed** (all 26 listed code points, leading NBSP, trailing FEFF, and a clean email). This verifies detector behavior, not existing profile data.
- Prior NestJS verification: 3 Jest suites / 8 tests passed and production build passed; NestJS source was not changed in this review.

## 2. Migration and database-role review

### `database/add-v2-global-account-email.sql`

- Targets only `QuanLyDaoTao_V2` and checks for required tables/collation.
- Adds nullable `dbo.users.email` as `nvarchar(254)` with `Vietnamese_100_CI_AS`, backfills it from linked student/employee profiles, and creates a filtered unique index for non-null addresses.
- Preflight checks:
  - An employee email with no linked user.
  - Duplicate profile emails after lowercasing; preflight first rejects any listed whitespace, so the migration does not trim or replace source email characters.
  - Conflicting normalized profile emails for one account.
  - ASCII whitespace U+0009-U+000D and U+0020; Unicode whitespace U+0085, U+00A0, U+1680, U+2000-U+200A, U+2028-U+2029, U+202F, U+205F, U+3000; and U+FEFF. Detection applies to every position, including leading/trailing characters, before backfill or index creation.
- Live schema check: `users.id` is `uniqueidentifier`; `users.email` is absent. `students.user_id` is non-null and `employees.user_id` is nullable; both profile `email` columns are nullable `nvarchar(254)`. Foreign keys from both profile `user_id` columns to `users.id` exist. `students.user_id` and non-NULL `employees.user_id` are unique.
- Read-only data counts: 1 user, 0 students, 0 employees, 0 non-NULL profile emails. Duplicate normalized email groups, conflicting normalized profile emails per user, missing linked users, unlinked employee email rows, whitespace matches, and >254-character profile emails all counted 0. Because profiles are empty, these zeroes do not validate representative legacy email data.
- `database/preflight-v2-global-account-email.sql` reports real-data counts without exposing email values and validates the detector using 29 in-memory vectors. It ran read-only on SQL Server; all vectors passed.
- The draft now performs profile checks and DDL/backfill in one `SERIALIZABLE` transaction, preventing ordinary concurrent profile changes from slipping between preflight and index creation. This can block writes; run in an approved maintenance window on a dedicated migration connection.
- The existing-state guard compares the normalized index filter to exactly `email IS NOT NULL`, requires an enabled/non-hypothetical index, and rejects additional key columns. This avoids treating a disabled or composite index as a completed migration.
- SQL Server's `Msg 207 Invalid column name 'email'` was caused by the static `UPDATE account.email` (and potentially `CREATE INDEX ... users(email)`) being compiled in the same batch as `ALTER TABLE ... ADD email`. The migration now runs both statements in `sp_executesql` after the `ALTER`, so SQL Server binds the new column at dynamic-batch compilation time. Preflight results use `#ProfileEmails`, which is visible to the nested dynamic batch; the table is dropped before commit and cleaned up in `CATCH`. No table variable is passed to `sp_executesql`.
- Re-running against the expected complete schema is a no-op. A partial or differently configured schema fails closed with an error instead of being silently accepted.
- The migration was checked with SQL Server `SET PARSEONLY`; this parsed but did not execute it.
- **Not executed.** The live schema still lacks `users.email`; the current Spring source depends on this column.

### `database/prepare-v2-backend-database-roles.sql`

- Requires two separate, pre-existing, non-privileged database users; placeholder values must be replaced before an approved run.
- Grants Spring read/write access to its listed tables, but explicitly denies Spring `INSERT`, `UPDATE`, and `DELETE` on `course_registrations` and `notifications`.
- Grants NestJS registration/notification reads and the intended registration/notification writes, while denying deletion there and denying NestJS writes to the Spring-owned table list, including accounts and profiles.
- The script checks required V2 tables and rejects broad built-in database roles for the application users. Review effective permissions and ownership/role membership on the target database before execution.
- **Not executed.** No database users, roles, or grants were changed in this acceptance review.

## 3. Endpoint-role matrix

The **current** column records enforcement found in source. The role columns are a recommendation only; no access has been enabled. Spring's interceptor currently permits the public auth paths below, allows any valid JWT for `/api/auth/me`, and requires `ADMIN` for every other `/api/**` path. NestJS uses JWT and route role guards; notification operations are scoped to the authenticated user. “Not present” means there is no corresponding self-service/lecturer route to grant.

| Endpoint group | Current enforcement | ADMIN proposal | STAFF proposal | LECTURER proposal | STUDENT proposal |
|---|---|---|---|---|---|
| Spring `POST /api/auth/login`, `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/refresh`, `/api/auth/logout` | Public through the interceptor; endpoint-specific validation may still apply | Public as required by the auth flow | Public as required by the auth flow | Public as required by the auth flow | Public as required by the auth flow |
| Spring `GET /api/auth/me` | Any valid JWT role | Allow | Allow | Allow | Allow |
| Spring account/RBAC: `/api/auth/accounts/**`, `/api/auth/roles`, `/api/auth/independent-roles` | ADMIN | Allow | Deny | Deny | Deny |
| Spring academic catalog, student/lecturer profiles, form options, course sections and assignments (including `/api/students/**`, `/api/lecturers/**`, `/api/departments/**`, `/api/courses/**`, `/api/course-sections/**`, and related `/api/**` controllers) | ADMIN | Allow | Consider scoped access after approval; profile-based provisioning may create the linked role account, not standalone accounts/RBAC | Deny existing admin endpoints | Deny existing admin endpoints |
| Spring student self-service views | No separately scoped endpoints identified; other Spring `/api/**` routes are ADMIN-only | No change | No change | No change | No access until a dedicated own-data route is designed and approved |
| Nest `GET course-registrations/open-sections`, `GET course-registrations/mine`, `POST course-registrations`, `DELETE course-registrations/:id` | STUDENT | Deny | Deny | Deny | Allow, with ownership enforced |
| Nest `GET course-registrations/admin/all`, `PATCH course-registrations/admin/:id/status`, and registration/course-section reports | ADMIN | Allow | Deny | Deny | Deny |
| Nest `GET notifications`, `PATCH notifications/:id/read` | Any authenticated role; operations are scoped to current user | Allow own notifications | Allow own notifications | Allow own notifications | Allow own notifications |
| Lecturer-specific own sections, rosters, schedules, and grade entry | No lecturer-specific endpoint identified | Not present | Not present | Not present; design dedicated scoped endpoints only after approval | Not present |

### Decisions requiring owner confirmation

1. May `STAFF` manage the listed academic catalog and student/lecturer profiles, including profile-triggered account provisioning, while remaining unable to manage standalone accounts or roles?
2. Should lecturers receive access to their own sections, rosters, schedules, and grades? These endpoints were not identified in the current API.
3. Should students receive new Spring self-service endpoints, or remain limited to existing NestJS registration and own-notification endpoints?

Until confirmed, retain the current enforcement; do not broaden roles based on this proposal.

## 4. Account/profile transaction and email checks

- Student creation runs transactionally; account provisioning, role linkage, student profile persistence, and account email synchronization are within the transaction. Updates also synchronize the normalized profile email with the linked account.
- Lecturer create/update/delete operations run transactionally. Creation validates the linked active `LECTURER` account (including a supplied user ID), persists the employee and lecturer records, and synchronizes account email in the same transaction. Update synchronizes email and deactivation state as applicable.
- Email normalization in the application trims and lowercases. Account provisioning checks account-email uniqueness; the database unique filtered index in the pending migration is the final cross-account constraint.
- Automated tests cover the changed synchronization/linkage behavior. Transaction rollback and SQL Server constraint behavior still need verification on a disposable QA database.
- Because `users.email` is not present in the live database, do not deploy/run the updated Spring source against it until the approved migration is applied.

### Rollback and data preservation

- The migration only adds a nullable column, copies normalized values from existing linked profiles, and adds a filtered unique index; it does not drop or rewrite the existing profile columns.
- In the revised draft, all data checks, `ALTER TABLE`, backfill, and index creation are inside one transaction with `XACT_ABORT` and `TRY/CATCH`. An error before commit rolls back the migration's changes.
- There is no post-commit down migration. Before the application starts writing `users.email`, a separately reviewed rollback could remove the index and added column, but it should only be performed after confirming no new values were written. After application writes begin, dropping the column would lose account-email data; export/reconcile it first and require explicit approval.
- Do not use database restore or drop-column rollback as an automatic response to an application issue.

## 5. Login query compatibility

`AuthController.findDatabaseUser()` continues to support username, account email, student code, and employee code. It now queries from `users` with `EXISTS` predicates for profile codes, so each matching account produces one row. It accepts a match only when exactly one account row is returned; zero or multiple accounts return the existing authentication failure (`401`) before role/password processing. This removes arbitrary account choice without changing JWT issuance, role loading, or account structure.

The actual SQL Server schema still lacks `users.email`, so the updated login query cannot run against that database until the migration is separately approved and applied. Authentication behavior was tested with the H2 test schema; no SQL Server login was attempted.

## 6. SQL Server QA checklist

Run only against a disposable or approved QA database. Back up/restore it as required; use test identities and non-production credentials. Do not treat this checklist as authorization to run migration or grant scripts.

### DBA approval and backup gate

- The live database was `ONLINE`, recovery model `FULL`, compatibility level `160`.
- A read-only query of `msdb.dbo.backupset` on the connected instance returned **no backup records** for this database. This does not rule out an externally managed backup, but backup currency and restorability are currently **unverified**. Do not apply the migration until the DBA confirms an approved recoverable backup and, preferably, a successful restore test.
- Because recovery is `FULL`, the DBA must also confirm the log-backup chain and available log/disk capacity. The migration uses a `SERIALIZABLE` transaction and may block profile/account writes; schedule an approved maintenance window and ensure no application writes are in progress.
- DBA approval must identify the exact SQL Server instance, target `QuanLyDaoTao_V2`, migration file/version, backup/restore point, and change window. The script's database-name guard is a second safeguard, not a substitute for selecting the right instance.

### Execution runbook (not executed)

1. In SSMS, connect to the approved instance and select `QuanLyDaoTao_V2`, or pass that database explicitly with `sqlcmd -d QuanLyDaoTao_V2`.
2. **Read-only command:** run the complete preflight and review every result set. It uses table variables and `SELECT`; it does not persist data. Stop if any whitespace, duplicate, conflict, unlinked-email, or orphan-link count is nonzero. Correct source records only through a separately approved data change; the migration will not trim or replace characters.

   ```powershell
   sqlcmd -S "<approved-server-or-instance>" -d "QuanLyDaoTao_V2" -E -b -i "D:\DoAnNam3\quanlydaotaoit24ab\database\preflight-v2-global-account-email.sql"
   ```

3. Before migration, ensure a recent full backup is available at an approved unique path. The following is a **DBA-only backup-file write**, not run here; use a new unique filename and follow organizational backup policy:

   ```sql
   BACKUP DATABASE [QuanLyDaoTao_V2]
   TO DISK = N'<approved-backup-path>\QuanLyDaoTao_V2_pre_email_YYYYMMDD_HHMMSS.bak'
   WITH COPY_ONLY, CHECKSUM, COMPRESSION, STATS = 10;

   RESTORE VERIFYONLY
   FROM DISK = N'<same-approved-backup-path>\QuanLyDaoTao_V2_pre_email_YYYYMMDD_HHMMSS.bak'
   WITH CHECKSUM;
   ```

   `BACKUP DATABASE` writes a backup file; it does not alter application rows/schema. `RESTORE VERIFYONLY` reads/verifies the backup but is not a substitute for a tested restore.

4. Only after explicit DBA/change-owner approval and successful preflight/backup, the DBA may run the migration. This command **changes schema and data** (adds `users.email`, backfills it, creates the unique index); it was not run:

   ```powershell
   sqlcmd -S "<approved-server-or-instance>" -d "QuanLyDaoTao_V2" -E -b -i "D:\DoAnNam3\quanlydaotaoit24ab\database\add-v2-global-account-email.sql"
   ```

   `-E` uses Windows integrated authentication. If the DBA uses SQL authentication, use the organization's secure credential prompt/secret mechanism; do not put a password on the command line.

5. After successful completion, run the read-only post-migration verification SQL below and confirm exactly one enabled unique key on `email`, the intended filter, and zero backfill mismatches. Then restart/reconnect the Spring service as required by the deployment procedure and test login with the existing ADMIN account. Do not create an ADMIN or reset its password as part of this fix.
6. If login still fails, capture the timestamp, exact SQL exception and surrounding Spring log lines; first verify the application's actual server/database (`SELECT DB_NAME()` using the same datasource) before diagnosing the next error. Do not infer a new cause without that log.

### Database readiness

- [ ] Confirm the target database is `QuanLyDaoTao_V2` and the schema matches the expected V2 tables.
- [ ] Run `database/preflight-v2-global-account-email.sql`; resolve duplicate normalized emails, account/profile conflicts, unlinked employee emails, any reported ASCII/Unicode whitespace, and orphan links before applying through the approved change process.
- [ ] After an approved migration, verify `users.email`, backfilled values, collation, and filtered unique index.
- [ ] Create separate least-privileged Spring and Nest database users. After separately approved grants, verify effective permissions: Spring cannot write registrations/notifications; Nest cannot write users, roles, student/employee/lecturer profiles, or other Spring-owned tables.
- [ ] Configure the two backends with their separate SQL credentials and compatible JWT settings. Keep secrets outside source control.
- [ ] Verify a usable ADMIN account and a valid active grading-scale record exist.

### Authentication and account provisioning

- [ ] Log in with a valid test account; verify issued JWT identity/roles and access to an allowed route.
- [ ] Verify missing, malformed, expired, and wrong-role tokens produce the expected `401`/`403` behavior.
- [ ] Refresh a token; verify the new token works and the rotated/revoked refresh token cannot be reused.
- [ ] Log out; verify the refresh credential is revoked and cannot be refreshed. Check access-token behavior against the implemented expiry/revocation design.
- [ ] Create a student through the profile API; verify user, `STUDENT` role link, profile link, and normalized email persist together.
- [ ] Create a lecturer through the profile API; verify employee/lecturer rows, user link, active `LECTURER` role link, and synchronized email persist together.
- [ ] Force a profile-creation failure after account provisioning in QA; verify the account and role link roll back and no orphan account remains.
- [ ] Try duplicate emails with case and surrounding whitespace variations; verify application and database constraints reject duplicates consistently.
- [ ] Try a supplied inactive account, an account without the required role, and a revoked role link for lecturer creation; verify rejection.

### Course registration and grading readiness

- [ ] Prepare an active grading-scale row through the approved data-management process; verify its status is `ACTIVE`.
- [ ] Create a course section in draft, assign an active lecturer, then open it with the active grading scale. Verify opening is rejected without the assignment or active scale.
- [ ] As a student, list open sections and register; verify registration persists in SQL Server and appears only in the student's own-registration view.
- [ ] Verify duplicate registration, capacity, and registration-window rules using the expected business cases.
- [ ] Cancel a student's own registration; verify the registration is no longer active and the expected record is retained (no unauthorized hard delete).
- [ ] As ADMIN, approve/reject a registration; verify status persistence and notification creation/read state.
- [ ] Verify registration reports against persisted records and confirm the student cannot access admin lists/status routes.
- [ ] Exercise the relevant flows with each backend's configured database identity and confirm denied cross-owner writes fail.

### Read-only post-migration verification SQL

Run only after an approved migration. These statements inspect metadata and return aggregate counts; they do not return email addresses or change data.

```sql
USE QuanLyDaoTao_V2;
GO

-- Confirm the new nullable column and its type/collation.
SELECT c.name, TYPE_NAME(c.user_type_id) AS data_type,
       c.max_length, c.is_nullable, c.collation_name
FROM sys.columns c
WHERE c.object_id = OBJECT_ID(N'dbo.users')
  AND c.name = N'email';

-- Confirm a unique filtered index on email with exactly the intended filter.
SELECT i.name, i.is_unique, i.has_filter, i.filter_definition,
       c.name AS key_column
FROM sys.indexes i
JOIN sys.index_columns ic
  ON ic.object_id = i.object_id AND ic.index_id = i.index_id
 AND ic.key_ordinal = 1
JOIN sys.columns c
  ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID(N'dbo.users')
  AND i.name = N'UX_users_email';

-- Check for duplicate normalized account emails; expected count is 0.
;WITH AccountEmails AS (
    SELECT id,
           LOWER(email)
               COLLATE Vietnamese_100_CI_AS AS email_key
    FROM dbo.users
    WHERE email IS NOT NULL
)
SELECT COUNT(*) AS duplicate_normalized_email_groups
FROM (
    SELECT email_key
    FROM AccountEmails
    WHERE email_key <> N''
    GROUP BY email_key
    HAVING COUNT(*) > 1
) d;

-- Check linked profile emails against their account email; expected mismatch count is 0.
;WITH Profiles AS (
    SELECT user_id, email FROM dbo.students WHERE email IS NOT NULL
    UNION ALL
    SELECT user_id, email FROM dbo.employees WHERE email IS NOT NULL
), Normalized AS (
    SELECT user_id,
           LOWER(email)
               COLLATE Vietnamese_100_CI_AS AS profile_email_key
    FROM Profiles
)
SELECT COUNT(*) AS profile_account_email_mismatches
FROM Normalized p
JOIN dbo.users u ON u.id = p.user_id
WHERE p.profile_email_key <> N''
  AND (u.email IS NULL OR
       LOWER(u.email) COLLATE Vietnamese_100_CI_AS <> p.profile_email_key);
GO
```

Run the standalone whitespace preflight before applying the migration; it detects and reports whitespace without rewriting any email values. Also verify the actual login endpoint in QA after migration rather than relying on metadata alone.

## 7. Grading-scale readiness

- The reviewed live database has **zero rows** in `grading_scales`.
- A course section must have a grading-scale ID; opening it additionally requires that scale to be `ACTIVE` and requires an active lecturer assignment.
- Therefore, before opening course sections, the team must define/approve the grading scale and populate at least one valid active record using an approved SQL/data-management process. The acceptance review did not add seed data or a scale-management API.
- Confirm the accepted status vocabulary and scale contents before preparing production data. Do not bypass these service validations.

## 8. Items eligible for Sprint 2

Sprint 2 is not started or authorized by this document. After Sprint 1's database prerequisites and real-SQL-Server checklist are completed, and the owner confirms the role decisions above, Sprint 2 may be planned against its approved scope. In particular:

- Any access-control changes must implement only the confirmed role matrix.
- Any grading-scale administration or seed-data work requires an approved scale definition and data-management decision.
- Do not begin Sprint 2 implementation before Sprint 1 acceptance is explicitly closed.
