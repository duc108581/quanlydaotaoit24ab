SET NOCOUNT ON;

IF DB_NAME() <> N'QuanLyDaoTao_V2'
    THROW 51200, 'This read-only preflight only targets QuanLyDaoTao_V2.', 1;

IF OBJECT_ID(N'dbo.users', N'U') IS NULL
   OR OBJECT_ID(N'dbo.students', N'U') IS NULL
   OR OBJECT_ID(N'dbo.employees', N'U') IS NULL
    THROW 51201, 'Required V2 profile tables are missing.', 1;

DECLARE @EmailWhitespaceCodePoints TABLE (
    code_point int PRIMARY KEY
);

INSERT INTO @EmailWhitespaceCodePoints (code_point) VALUES
    (9), (10), (11), (12), (13), (32),
    (133), (160), (5760),
    (8192), (8193), (8194), (8195), (8196), (8197),
    (8198), (8199), (8200), (8201), (8202),
    (8232), (8233), (8239), (8287), (12288), (65279);

-- Counts affected rows by source table without returning email values.
SELECT N'students' AS source_table,
       (SELECT COUNT_BIG(*) FROM dbo.students WHERE email IS NOT NULL) AS nonnull_email_rows,
       (SELECT COUNT_BIG(*)
        FROM dbo.students profile
        WHERE profile.email IS NOT NULL
          AND EXISTS (
              SELECT 1
              FROM @EmailWhitespaceCodePoints whitespace
              WHERE CHARINDEX(
                  NCHAR(whitespace.code_point),
                  profile.email COLLATE Latin1_General_100_BIN2
              ) > 0
          )) AS rows_with_disallowed_whitespace
UNION ALL
SELECT N'employees',
       (SELECT COUNT_BIG(*) FROM dbo.employees WHERE email IS NOT NULL),
       (SELECT COUNT_BIG(*)
        FROM dbo.employees profile
        WHERE profile.email IS NOT NULL
          AND EXISTS (
              SELECT 1
              FROM @EmailWhitespaceCodePoints whitespace
              WHERE CHARINDEX(
                  NCHAR(whitespace.code_point),
                  profile.email COLLATE Latin1_General_100_BIN2
              ) > 0
          ));

-- Identify affected code points by count only; no email contents are exposed.
;WITH ProfileEmails AS (
    SELECT N'students' AS source_table, email
    FROM dbo.students
    WHERE email IS NOT NULL
    UNION ALL
    SELECT N'employees', email
    FROM dbo.employees
    WHERE email IS NOT NULL
)
SELECT profile.source_table,
       whitespace.code_point,
       COUNT_BIG(*) AS affected_rows
FROM ProfileEmails profile
CROSS JOIN @EmailWhitespaceCodePoints whitespace
WHERE CHARINDEX(
    NCHAR(whitespace.code_point),
    profile.email COLLATE Latin1_General_100_BIN2
) > 0
GROUP BY profile.source_table, whitespace.code_point
ORDER BY profile.source_table, whitespace.code_point;

DECLARE @NormalizedProfileEmails TABLE (
    user_id uniqueidentifier NOT NULL,
    email_key nvarchar(254) COLLATE Vietnamese_100_CI_AS NOT NULL
);

INSERT INTO @NormalizedProfileEmails (user_id, email_key)
SELECT user_id, LOWER(email)
FROM dbo.students
WHERE user_id IS NOT NULL AND email IS NOT NULL AND email <> N''
UNION ALL
SELECT user_id, LOWER(email)
FROM dbo.employees
WHERE user_id IS NOT NULL AND email IS NOT NULL AND email <> N'';

SELECT N'normalized_duplicate_email_groups' AS check_name, COUNT_BIG(*) AS finding_count
FROM (
    SELECT email_key
    FROM @NormalizedProfileEmails
    GROUP BY email_key
    HAVING COUNT(DISTINCT user_id) > 1
) duplicates
UNION ALL
SELECT N'accounts_with_conflicting_profile_emails', COUNT_BIG(*)
FROM (
    SELECT user_id
    FROM @NormalizedProfileEmails
    GROUP BY user_id
    HAVING COUNT(DISTINCT email_key) > 1
) conflicts
UNION ALL
SELECT N'employees_with_nonempty_email_without_user', COUNT_BIG(*)
FROM dbo.employees
WHERE email IS NOT NULL AND email <> N'' AND user_id IS NULL
UNION ALL
SELECT N'student_profile_rows_with_missing_user', COUNT_BIG(*)
FROM dbo.students profile
LEFT JOIN dbo.users account ON account.id = profile.user_id
WHERE account.id IS NULL
UNION ALL
SELECT N'employee_profile_rows_with_missing_user', COUNT_BIG(*)
FROM dbo.employees profile
LEFT JOIN dbo.users account ON account.id = profile.user_id
WHERE profile.user_id IS NOT NULL AND account.id IS NULL;

-- Exercise the detector on each code point plus boundary-position cases.
DECLARE @WhitespaceTestCases TABLE (
    test_name nvarchar(40) NOT NULL,
    sample_value nvarchar(254) NOT NULL,
    expected_invalid bit NOT NULL
);

INSERT INTO @WhitespaceTestCases (test_name, sample_value, expected_invalid)
SELECT CONCAT(N'codepoint-', code_point),
       N'a' + NCHAR(code_point) + N'b',
       1
FROM @EmailWhitespaceCodePoints
UNION ALL
SELECT N'leading-NBSP', NCHAR(160) + N'a@example.test', 1
UNION ALL
SELECT N'trailing-FEFF', N'a@example.test' + NCHAR(65279), 1
UNION ALL
SELECT N'clean-email', N'a@example.test', 0;

DECLARE @WhitespaceTestResults TABLE (
    test_name nvarchar(40) NOT NULL,
    expected_invalid bit NOT NULL,
    actual_invalid bit NOT NULL
);

INSERT INTO @WhitespaceTestResults (test_name, expected_invalid, actual_invalid)
SELECT test.test_name,
       test.expected_invalid,
       CASE WHEN EXISTS (
           SELECT 1
           FROM @EmailWhitespaceCodePoints whitespace
           WHERE CHARINDEX(
               NCHAR(whitespace.code_point),
               test.sample_value COLLATE Latin1_General_100_BIN2
           ) > 0
       ) THEN 1 ELSE 0 END
FROM @WhitespaceTestCases test;

SELECT test_name, expected_invalid, actual_invalid
FROM @WhitespaceTestResults
ORDER BY test_name;

IF EXISTS (
    SELECT 1
    FROM @WhitespaceTestResults
    WHERE expected_invalid <> actual_invalid
)
    THROW 51202, 'The SQL Server email whitespace detector failed a test vector.', 1;
