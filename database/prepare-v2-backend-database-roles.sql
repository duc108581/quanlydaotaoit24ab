SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'QuanLyDaoTao_V2'
    THROW 51100, 'This permissions script only targets QuanLyDaoTao_V2.', 1;

DECLARE @SpringDatabaseUser sysname = N'<SPRING_SQL_DATABASE_USER>';
DECLARE @NestDatabaseUser sysname = N'<NEST_SQL_DATABASE_USER>';

IF @SpringDatabaseUser = N'<SPRING_SQL_DATABASE_USER>'
   OR @NestDatabaseUser = N'<NEST_SQL_DATABASE_USER>'
    THROW 51101, 'Replace both database-user placeholders before applying this script.', 1;

IF DATABASE_PRINCIPAL_ID(@SpringDatabaseUser) IS NULL
   OR DATABASE_PRINCIPAL_ID(@NestDatabaseUser) IS NULL
    THROW 51102, 'Create both database users separately before applying application-role grants.', 1;

IF @SpringDatabaseUser = @NestDatabaseUser
    THROW 51103, 'Spring Boot and NestJS must use separate database users.', 1;

IF DATABASE_PRINCIPAL_ID(@SpringDatabaseUser) IN (1, 2)
   OR DATABASE_PRINCIPAL_ID(@NestDatabaseUser) IN (1, 2)
   OR @SpringDatabaseUser IN (N'dbo', N'sa', N'sysadmin')
   OR @NestDatabaseUser IN (N'dbo', N'sa', N'sysadmin')
    THROW 51106, 'Use separate non-privileged SQL database users, not dbo, sa, or sysadmin.', 1;

IF EXISTS (
    SELECT 1
    FROM sys.database_role_members membership
    INNER JOIN sys.database_principals member
        ON member.principal_id = membership.member_principal_id
    INNER JOIN sys.database_principals role
        ON role.principal_id = membership.role_principal_id
    WHERE member.name IN (@SpringDatabaseUser, @NestDatabaseUser)
      AND role.name IN (N'db_owner', N'db_datareader', N'db_datawriter', N'db_ddladmin')
)
    THROW 51104, 'Application users must not be members of broad built-in database roles.', 1;

DECLARE @SpringReadTables TABLE (table_name sysname PRIMARY KEY);
DECLARE @SpringWriteTables TABLE (table_name sysname PRIMARY KEY);
DECLARE @NestReadTables TABLE (table_name sysname PRIMARY KEY);
DECLARE @NestWriteTables TABLE (table_name sysname PRIMARY KEY);

INSERT INTO @SpringReadTables (table_name) VALUES
    (N'users'), (N'roles'), (N'user_roles'), (N'refresh_tokens'),
    (N'students'), (N'employees'), (N'lecturers'),
    (N'student_classes'), (N'student_class_history'),
    (N'student_statuses'), (N'student_status_history'),
    (N'departments'), (N'majors'), (N'training_programs'),
    (N'academic_years'), (N'school_years'), (N'semesters'),
    (N'courses'), (N'course_sections'), (N'lecturer_course_sections'),
    (N'buildings'), (N'rooms'), (N'grading_scales'),
    (N'course_registrations');

INSERT INTO @SpringWriteTables (table_name) VALUES
    (N'users'), (N'roles'), (N'user_roles'), (N'refresh_tokens'),
    (N'students'), (N'employees'), (N'lecturers'),
    (N'student_classes'), (N'student_class_history'),
    (N'student_statuses'), (N'student_status_history'),
    (N'departments'), (N'majors'), (N'training_programs'),
    (N'academic_years'), (N'school_years'), (N'semesters'),
    (N'courses'), (N'course_sections'), (N'lecturer_course_sections'),
    (N'buildings'), (N'rooms');

INSERT INTO @NestReadTables (table_name) VALUES
    (N'students'), (N'courses'), (N'course_sections'),
    (N'course_registrations'), (N'notifications');

INSERT INTO @NestWriteTables (table_name) VALUES
    (N'course_registrations'), (N'notifications');

IF EXISTS (
    SELECT table_name FROM @SpringReadTables
    UNION
    SELECT table_name FROM @SpringWriteTables
    UNION
    SELECT table_name FROM @NestReadTables
    UNION
    SELECT table_name FROM @NestWriteTables
    EXCEPT
    SELECT name FROM sys.tables WHERE schema_id = SCHEMA_ID(N'dbo')
)
    THROW 51105, 'One or more expected V2 tables are missing; review the permission list.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF DATABASE_PRINCIPAL_ID(N'qltd_spring_runtime') IS NULL
        CREATE ROLE qltd_spring_runtime;
    IF DATABASE_PRINCIPAL_ID(N'qltd_nest_runtime') IS NULL
        CREATE ROLE qltd_nest_runtime;

    IF EXISTS (
        SELECT 1
        FROM sys.database_role_members membership
        WHERE (membership.role_principal_id = DATABASE_PRINCIPAL_ID(N'qltd_nest_runtime')
               AND membership.member_principal_id = DATABASE_PRINCIPAL_ID(@SpringDatabaseUser))
           OR (membership.role_principal_id = DATABASE_PRINCIPAL_ID(N'qltd_spring_runtime')
               AND membership.member_principal_id = DATABASE_PRINCIPAL_ID(@NestDatabaseUser))
    )
        THROW 51107, 'A database user is already assigned to the other backend role.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.database_role_members
        WHERE role_principal_id = DATABASE_PRINCIPAL_ID(N'qltd_spring_runtime')
          AND member_principal_id = DATABASE_PRINCIPAL_ID(@SpringDatabaseUser)
    )
        EXEC (N'ALTER ROLE [qltd_spring_runtime] ADD MEMBER ' + QUOTENAME(@SpringDatabaseUser));

    IF NOT EXISTS (
        SELECT 1
        FROM sys.database_role_members
        WHERE role_principal_id = DATABASE_PRINCIPAL_ID(N'qltd_nest_runtime')
          AND member_principal_id = DATABASE_PRINCIPAL_ID(@NestDatabaseUser)
    )
        EXEC (N'ALTER ROLE [qltd_nest_runtime] ADD MEMBER ' + QUOTENAME(@NestDatabaseUser));

    DECLARE @TableName sysname;
    DECLARE @Sql nvarchar(max);

    DECLARE SpringReadCursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT table_name FROM @SpringReadTables;
    OPEN SpringReadCursor;
    FETCH NEXT FROM SpringReadCursor INTO @TableName;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'GRANT SELECT ON OBJECT::dbo.' + QUOTENAME(@TableName)
            + N' TO [qltd_spring_runtime];';
        EXEC sys.sp_executesql @Sql;
        FETCH NEXT FROM SpringReadCursor INTO @TableName;
    END;
    CLOSE SpringReadCursor;
    DEALLOCATE SpringReadCursor;

    DECLARE SpringWriteCursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT table_name FROM @SpringWriteTables;
    OPEN SpringWriteCursor;
    FETCH NEXT FROM SpringWriteCursor INTO @TableName;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'GRANT INSERT, UPDATE ON OBJECT::dbo.' + QUOTENAME(@TableName)
            + N' TO [qltd_spring_runtime];';
        EXEC sys.sp_executesql @Sql;
        FETCH NEXT FROM SpringWriteCursor INTO @TableName;
    END;
    CLOSE SpringWriteCursor;
    DEALLOCATE SpringWriteCursor;

    DECLARE NestReadCursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT table_name FROM @NestReadTables;
    OPEN NestReadCursor;
    FETCH NEXT FROM NestReadCursor INTO @TableName;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'GRANT SELECT ON OBJECT::dbo.' + QUOTENAME(@TableName)
            + N' TO [qltd_nest_runtime];';
        EXEC sys.sp_executesql @Sql;
        FETCH NEXT FROM NestReadCursor INTO @TableName;
    END;
    CLOSE NestReadCursor;
    DEALLOCATE NestReadCursor;

    DECLARE NestWriteCursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT table_name FROM @NestWriteTables;
    OPEN NestWriteCursor;
    FETCH NEXT FROM NestWriteCursor INTO @TableName;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'GRANT INSERT, UPDATE ON OBJECT::dbo.' + QUOTENAME(@TableName)
            + N' TO [qltd_nest_runtime];';
        EXEC sys.sp_executesql @Sql;
        FETCH NEXT FROM NestWriteCursor INTO @TableName;
    END;
    CLOSE NestWriteCursor;
    DEALLOCATE NestWriteCursor;

    DECLARE NestDenySpringWritesCursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT table_name FROM @SpringWriteTables;
    OPEN NestDenySpringWritesCursor;
    FETCH NEXT FROM NestDenySpringWritesCursor INTO @TableName;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'DENY INSERT, UPDATE, DELETE ON OBJECT::dbo.' + QUOTENAME(@TableName)
            + N' TO [qltd_nest_runtime];';
        EXEC sys.sp_executesql @Sql;
        FETCH NEXT FROM NestDenySpringWritesCursor INTO @TableName;
    END;
    CLOSE NestDenySpringWritesCursor;
    DEALLOCATE NestDenySpringWritesCursor;

    DENY INSERT, UPDATE, DELETE ON OBJECT::dbo.course_registrations TO [qltd_spring_runtime];
    DENY INSERT, UPDATE, DELETE ON OBJECT::dbo.notifications TO [qltd_spring_runtime];
    DENY DELETE ON OBJECT::dbo.course_registrations TO [qltd_nest_runtime];
    DENY DELETE ON OBJECT::dbo.notifications TO [qltd_nest_runtime];

    COMMIT TRANSACTION;
    PRINT N'Applied separate least-privilege roles for Spring Boot and NestJS.';
END TRY
BEGIN CATCH
    IF CURSOR_STATUS('local', 'SpringReadCursor') >= 0
        CLOSE SpringReadCursor;
    IF CURSOR_STATUS('local', 'SpringReadCursor') > -3
    BEGIN
        DEALLOCATE SpringReadCursor;
    END;
    IF CURSOR_STATUS('local', 'SpringWriteCursor') >= 0
        CLOSE SpringWriteCursor;
    IF CURSOR_STATUS('local', 'SpringWriteCursor') > -3
    BEGIN
        DEALLOCATE SpringWriteCursor;
    END;
    IF CURSOR_STATUS('local', 'NestReadCursor') >= 0
        CLOSE NestReadCursor;
    IF CURSOR_STATUS('local', 'NestReadCursor') > -3
    BEGIN
        DEALLOCATE NestReadCursor;
    END;
    IF CURSOR_STATUS('local', 'NestWriteCursor') >= 0
        CLOSE NestWriteCursor;
    IF CURSOR_STATUS('local', 'NestWriteCursor') > -3
    BEGIN
        DEALLOCATE NestWriteCursor;
    END;
    IF CURSOR_STATUS('local', 'NestDenySpringWritesCursor') >= 0
        CLOSE NestDenySpringWritesCursor;
    IF CURSOR_STATUS('local', 'NestDenySpringWritesCursor') > -3
    BEGIN
        DEALLOCATE NestDenySpringWritesCursor;
    END;
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
