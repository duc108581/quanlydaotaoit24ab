SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'QuanLyDaoTao_V2'
    THROW 51000, 'This migration only targets QuanLyDaoTao_V2.', 1;

IF OBJECT_ID(N'dbo.users', N'U') IS NULL
   OR OBJECT_ID(N'dbo.students', N'U') IS NULL
   OR OBJECT_ID(N'dbo.employees', N'U') IS NULL
    THROW 51001, 'Required V2 account/profile tables are missing.', 1;

IF NOT EXISTS (
    SELECT 1
    FROM sys.fn_helpcollations()
    WHERE name = N'Vietnamese_100_CI_AS'
)
    THROW 51006, 'Required case-insensitive Vietnamese collation is unavailable.', 1;

IF COL_LENGTH(N'dbo.users', N'email') IS NOT NULL
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys.columns
        WHERE object_id = OBJECT_ID(N'dbo.users')
          AND name = N'email'
          AND system_type_id = TYPE_ID(N'nvarchar')
          AND max_length = 508
          AND is_nullable = 1
          AND collation_name = N'Vietnamese_100_CI_AS'
    )
       AND EXISTS (
        SELECT 1
     FROM sys.indexes emailIndex
     WHERE emailIndex.object_id = OBJECT_ID(N'dbo.users')
       AND emailIndex.name = N'UX_users_email'
       AND emailIndex.is_unique = 1
       AND emailIndex.is_disabled = 0
       AND emailIndex.is_hypothetical = 0
       AND emailIndex.has_filter = 1
       AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
             LOWER(emailIndex.filter_definition), N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N'')
              = N'emailisnotnull'
       AND NOT EXISTS (
           SELECT 1
           FROM sys.index_columns extraKey
           WHERE extraKey.object_id = emailIndex.object_id
             AND extraKey.index_id = emailIndex.index_id
             AND extraKey.key_ordinal > 1
       )
    )
       AND EXISTS (
        SELECT 1
        FROM sys.index_columns indexColumn
        INNER JOIN sys.columns columnDefinition
            ON columnDefinition.object_id = indexColumn.object_id
           AND columnDefinition.column_id = indexColumn.column_id
        WHERE indexColumn.object_id = OBJECT_ID(N'dbo.users')
          AND indexColumn.index_id = INDEXPROPERTY(
                OBJECT_ID(N'dbo.users'), N'UX_users_email', N'IndexId')
          AND indexColumn.key_ordinal = 1
          AND columnDefinition.name = N'email'
    )
    BEGIN
        PRINT N'Global account email migration is already applied.';
        RETURN;
    END;

    THROW 51002, 'dbo.users.email already exists but does not match the expected migration state.', 1;
END;

BEGIN TRY
    SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.users', N'email') IS NOT NULL
        THROW 51008, 'dbo.users.email appeared after the initial state check; review the schema before retrying.', 1;

    DECLARE @EmailWhitespaceCodePoints TABLE (
        code_point int PRIMARY KEY
    );

    INSERT INTO @EmailWhitespaceCodePoints (code_point) VALUES
        (9), (10), (11), (12), (13), (32),
        (133), (160), (5760),
        (8192), (8193), (8194), (8195), (8196), (8197),
        (8198), (8199), (8200), (8201), (8202),
        (8232), (8233), (8239), (8287), (12288), (65279);

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT email FROM dbo.students WHERE email IS NOT NULL
            UNION ALL
            SELECT email FROM dbo.employees WHERE email IS NOT NULL
        ) profileEmails
        CROSS JOIN @EmailWhitespaceCodePoints whitespace
        WHERE CHARINDEX(
            NCHAR(whitespace.code_point),
            profileEmails.email COLLATE Latin1_General_100_BIN2
        ) > 0
    )
        THROW 51009, 'A profile email contains ASCII or Unicode whitespace; correct the source value before migration.', 1;

    IF EXISTS (
        SELECT 1
        FROM dbo.employees
        WHERE email IS NOT NULL
          AND email <> N''
          AND user_id IS NULL
    )
        THROW 51003, 'An employee has an email but no linked user account; resolve it before migration.', 1;

    IF OBJECT_ID(N'tempdb..#ProfileEmails') IS NOT NULL
        DROP TABLE #ProfileEmails;

    CREATE TABLE #ProfileEmails (
        user_id uniqueidentifier NOT NULL,
        email_key nvarchar(254) COLLATE Vietnamese_100_CI_AS NOT NULL
    );

    ;WITH ProfileSources AS (
        SELECT user_id, email
        FROM dbo.students
        WHERE user_id IS NOT NULL
          AND email IS NOT NULL
        UNION ALL
        SELECT user_id, email
        FROM dbo.employees
        WHERE user_id IS NOT NULL
          AND email IS NOT NULL
    ), NormalizedProfileEmails AS (
        SELECT user_id,
               LOWER(email) AS email_key
        FROM ProfileSources
    )
    INSERT INTO #ProfileEmails (user_id, email_key)
    SELECT user_id, email_key
    FROM NormalizedProfileEmails
    WHERE email_key <> N'';

    IF EXISTS (
        SELECT email_key
        FROM #ProfileEmails
        GROUP BY email_key
        HAVING COUNT(DISTINCT user_id) > 1
    )
        THROW 51004, 'Multiple accounts use the same normalized email; resolve duplicates before migration.', 1;

    IF EXISTS (
        SELECT user_id
        FROM #ProfileEmails
        GROUP BY user_id
        HAVING COUNT(DISTINCT email_key) > 1
    )
        THROW 51005, 'A linked account has conflicting profile emails; resolve the conflict before migration.', 1;

    ALTER TABLE dbo.users
        ADD email nvarchar(254) COLLATE Vietnamese_100_CI_AS NULL;

    DECLARE @BackfillAndIndexSql nvarchar(max) = N'
        ;WITH AccountEmails AS (
            SELECT user_id, MIN(email_key) AS email
            FROM #ProfileEmails
            GROUP BY user_id
        )
        UPDATE account
        SET account.email = profile.email
        FROM dbo.users account
        INNER JOIN AccountEmails profile ON profile.user_id = account.id;

        CREATE UNIQUE INDEX UX_users_email
            ON dbo.users(email)
            WHERE email IS NOT NULL;';

    EXEC sys.sp_executesql @BackfillAndIndexSql;

    DROP TABLE #ProfileEmails;
    COMMIT TRANSACTION;
    SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
    PRINT N'Added normalized, globally unique account email to dbo.users.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    IF OBJECT_ID(N'tempdb..#ProfileEmails') IS NOT NULL
        DROP TABLE #ProfileEmails;
    SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
    THROW;
END CATCH;
