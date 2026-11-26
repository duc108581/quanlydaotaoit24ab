/*
    Incremental integrity and index migration for QuanLyDaoTao_V2.
    Run with QuanLyDaoTao_V2 selected as the current database.
    This script does not create/drop tables, migrate data, or add triggers.
*/

IF DB_NAME() <> N'QuanLyDaoTao_V2'
BEGIN
    ;THROW 51000, 'Select QuanLyDaoTao_V2 before running this migration.', 1;
END;

DECLARE @databaseCollation sysname;
SELECT @databaseCollation = collation_name
FROM sys.databases
WHERE database_id = DB_ID();

IF @databaseCollation <> N'Vietnamese_100_CS_AS'
BEGIN
    ;THROW 51001, 'QuanLyDaoTao_V2 must use Vietnamese_100_CS_AS collation.', 1;
END;

IF OBJECT_ID(N'dbo.grading_scales', N'U') IS NULL
   OR OBJECT_ID(N'dbo.grading_scale_rules', N'U') IS NULL
   OR OBJECT_ID(N'dbo.course_section_schedules', N'U') IS NULL
   OR OBJECT_ID(N'dbo.lecturer_course_sections', N'U') IS NULL
   OR OBJECT_ID(N'dbo.registration_final_grades', N'U') IS NULL
   OR OBJECT_ID(N'dbo.registration_component_grades', N'U') IS NULL
   OR OBJECT_ID(N'dbo.exam_results', N'U') IS NULL
BEGIN
    ;THROW 51002, 'A required V2 table is missing; no migration changes were made.', 1;
END;

IF COL_LENGTH(N'dbo.grading_scales', N'minimum_score') IS NULL
   OR COL_LENGTH(N'dbo.grading_scales', N'maximum_score') IS NULL
   OR COL_LENGTH(N'dbo.grading_scale_rules', N'minimum_score') IS NULL
   OR COL_LENGTH(N'dbo.grading_scale_rules', N'maximum_score') IS NULL
   OR COL_LENGTH(N'dbo.course_section_schedules', N'weekday') IS NULL
   OR COL_LENGTH(N'dbo.course_section_schedules', N'start_time') IS NULL
   OR COL_LENGTH(N'dbo.course_section_schedules', N'end_time') IS NULL
   OR COL_LENGTH(N'dbo.course_section_schedules', N'schedule_type') IS NULL
   OR COL_LENGTH(N'dbo.course_section_schedules', N'room_id') IS NULL
   OR COL_LENGTH(N'dbo.lecturer_course_sections', N'course_section_id') IS NULL
   OR COL_LENGTH(N'dbo.lecturer_course_sections', N'deleted_at') IS NULL
   OR COL_LENGTH(N'dbo.lecturer_course_sections', N'is_active') IS NULL
   OR COL_LENGTH(N'dbo.registration_final_grades', N'course_section_id') IS NULL
   OR COL_LENGTH(N'dbo.registration_final_grades', N'grading_scale_id') IS NULL
   OR COL_LENGTH(N'dbo.registration_final_grades', N'grading_scale_rule_id') IS NULL
   OR COL_LENGTH(N'dbo.registration_component_grades', N'course_registration_id') IS NULL
   OR COL_LENGTH(N'dbo.registration_component_grades', N'grade_component_id') IS NULL
   OR COL_LENGTH(N'dbo.registration_component_grades', N'course_section_id') IS NULL
   OR COL_LENGTH(N'dbo.exam_results', N'exam_id') IS NULL
   OR COL_LENGTH(N'dbo.exam_results', N'course_registration_id') IS NULL
   OR COL_LENGTH(N'dbo.exam_results', N'course_section_id') IS NULL
BEGIN
    ;THROW 51003, 'A required V2 column is missing; no migration changes were made.', 1;
END;

DECLARE @ExpectedIndexes TABLE (
    table_name sysname NOT NULL,
    index_name sysname NOT NULL,
    is_unique bit NOT NULL,
    filter_definition nvarchar(200) NULL
);

DECLARE @ExpectedIndexColumns TABLE (
    table_name sysname NOT NULL,
    index_name sysname NOT NULL,
    key_ordinal int NOT NULL,
    column_name sysname NOT NULL
);

INSERT INTO @ExpectedIndexes (table_name, index_name, is_unique, filter_definition)
VALUES
    (N'lecturer_course_sections', N'UX_lecturer_course_sections_active', 1,
     N'DELETED_AT IS NULL AND IS_ACTIVE = 1'),
    (N'registration_final_grades', N'IX_registration_final_grades_section_scale', 0, NULL),
    (N'registration_final_grades', N'IX_registration_final_grades_rule_scale', 0, NULL),
    (N'registration_component_grades', N'IX_registration_component_grades_registration_section', 0, NULL),
    (N'registration_component_grades', N'IX_registration_component_grades_component_section', 0, NULL),
    (N'exam_results', N'IX_exam_results_exam_section', 0, NULL),
    (N'exam_results', N'IX_exam_results_registration_section', 0, NULL);

INSERT INTO @ExpectedIndexColumns (table_name, index_name, key_ordinal, column_name)
VALUES
    (N'lecturer_course_sections', N'UX_lecturer_course_sections_active', 1, N'course_section_id'),
    (N'registration_final_grades', N'IX_registration_final_grades_section_scale', 1, N'course_section_id'),
    (N'registration_final_grades', N'IX_registration_final_grades_section_scale', 2, N'grading_scale_id'),
    (N'registration_final_grades', N'IX_registration_final_grades_rule_scale', 1, N'grading_scale_rule_id'),
    (N'registration_final_grades', N'IX_registration_final_grades_rule_scale', 2, N'grading_scale_id'),
    (N'registration_component_grades', N'IX_registration_component_grades_registration_section', 1, N'course_registration_id'),
    (N'registration_component_grades', N'IX_registration_component_grades_registration_section', 2, N'course_section_id'),
    (N'registration_component_grades', N'IX_registration_component_grades_component_section', 1, N'grade_component_id'),
    (N'registration_component_grades', N'IX_registration_component_grades_component_section', 2, N'course_section_id'),
    (N'exam_results', N'IX_exam_results_exam_section', 1, N'exam_id'),
    (N'exam_results', N'IX_exam_results_exam_section', 2, N'course_section_id'),
    (N'exam_results', N'IX_exam_results_registration_section', 1, N'course_registration_id'),
    (N'exam_results', N'IX_exam_results_registration_section', 2, N'course_section_id');

IF EXISTS (
    SELECT 1
    FROM @ExpectedIndexes AS expected
    JOIN sys.tables AS table_object
      ON table_object.name = expected.table_name
     AND SCHEMA_NAME(table_object.schema_id) = N'dbo'
    JOIN sys.indexes AS actual
      ON actual.object_id = table_object.object_id
     AND actual.name = expected.index_name
    WHERE actual.is_unique <> expected.is_unique
       OR actual.type <> 2
       OR actual.is_disabled = 1
       OR actual.is_hypothetical = 1
       OR ISNULL(actual.has_filter, 0) <> CASE WHEN expected.filter_definition IS NULL THEN 0 ELSE 1 END
       OR ISNULL(
            UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                actual.filter_definition, N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N''), NCHAR(9), N'')),
            N'<NULL>'
          ) <> ISNULL(
            UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                expected.filter_definition, N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N''), NCHAR(9), N'')),
            N'<NULL>'
          )
       OR (
            SELECT COUNT(*)
            FROM sys.index_columns AS actual_column
            WHERE actual_column.object_id = actual.object_id
              AND actual_column.index_id = actual.index_id
              AND actual_column.key_ordinal > 0
          ) <> (
            SELECT COUNT(*)
            FROM @ExpectedIndexColumns AS expected_column
            WHERE expected_column.table_name = expected.table_name
              AND expected_column.index_name = expected.index_name
          )
       OR EXISTS (
            SELECT 1
            FROM @ExpectedIndexColumns AS expected_column
            LEFT JOIN sys.index_columns AS actual_column
              ON actual_column.object_id = actual.object_id
             AND actual_column.index_id = actual.index_id
             AND actual_column.key_ordinal = expected_column.key_ordinal
            LEFT JOIN sys.columns AS actual_field
              ON actual_field.object_id = actual_column.object_id
             AND actual_field.column_id = actual_column.column_id
             AND actual_field.name = expected_column.column_name
            WHERE expected_column.table_name = expected.table_name
              AND expected_column.index_name = expected.index_name
              AND actual_field.column_id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM sys.index_columns AS actual_column
            WHERE actual_column.object_id = actual.object_id
              AND actual_column.index_id = actual.index_id
              AND (
                  actual_column.is_included_column = 1
                  OR actual_column.is_descending_key = 1
              )
       )
)
BEGIN
    ;THROW 51008, 'An existing migration index has a different definition; review it before proceeding.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints AS existing_check
    WHERE existing_check.parent_object_id = OBJECT_ID(N'dbo.grading_scales')
      AND existing_check.name = N'CK_grading_scales_score_range_0_10'
      AND (
          existing_check.is_disabled = 1
          OR existing_check.is_not_trusted = 1
          OR UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                existing_check.definition, N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N''), NCHAR(9), N''))
             <> N'MINIMUM_SCOREISNOTNULLANDMAXIMUM_SCOREISNOTNULLANDMINIMUM_SCORE=0ANDMAXIMUM_SCORE=10'
      )
)
BEGIN
    ;THROW 51009, 'The existing grading-scale range check has a different or untrusted definition.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints AS existing_check
    WHERE existing_check.parent_object_id = OBJECT_ID(N'dbo.grading_scale_rules')
      AND existing_check.name = N'CK_grading_scale_rules_score_range_0_10'
      AND (
          existing_check.is_disabled = 1
          OR existing_check.is_not_trusted = 1
          OR UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                existing_check.definition, N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N''), NCHAR(9), N''))
             <> N'MINIMUM_SCOREISNOTNULLANDMAXIMUM_SCOREISNOTNULLANDMINIMUM_SCORE>=0ANDMAXIMUM_SCORE<=10ANDMINIMUM_SCORE<MAXIMUM_SCORE'
      )
)
BEGIN
    ;THROW 51010, 'The existing grading-rule range check has a different or untrusted definition.', 1;
END;

DECLARE @scheduleTimeDefinition nvarchar(max);
DECLARE @scheduleTypeDefinition nvarchar(max);
DECLARE @normalizedScheduleTimeDefinition nvarchar(max);
DECLARE @normalizedScheduleTypeDefinition nvarchar(max);

SELECT @scheduleTimeDefinition = definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.course_section_schedules')
  AND name = N'CK_course_section_schedules_time';

SELECT @scheduleTypeDefinition = definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.course_section_schedules')
  AND name = N'CK_course_section_schedules_type';

SET @normalizedScheduleTimeDefinition =
    UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        @scheduleTimeDefinition, N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N''), NCHAR(9), N''));

SET @normalizedScheduleTypeDefinition =
    UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        @scheduleTypeDefinition, N'[', N''), N']', N''), N' ', N''), N'(', N''), N')', N''), NCHAR(9), N''));

IF @scheduleTimeDefinition IS NOT NULL
   AND @normalizedScheduleTimeDefinition NOT IN (
       N'WEEKDAYISNULLANDSTART_TIMEISNULLANDEND_TIMEISNULLORWEEKDAYISNOTNULLANDSTART_TIMEISNOTNULLANDEND_TIMEISNOTNULLANDSTART_TIME<END_TIME',
       N'WEEKDAYISNOTNULLANDWEEKDAY>=1ANDWEEKDAY<=7ANDSTART_TIMEISNOTNULLANDEND_TIMEISNOTNULLANDSTART_TIME<END_TIME'
   )
BEGIN
    ;THROW 51011, 'The existing schedule-time check has an unknown definition; review it before migrating.', 1;
END;

IF @scheduleTypeDefinition IS NOT NULL
   AND @normalizedScheduleTypeDefinition NOT IN (
       N'SCHEDULE_TYPE=N''ONSITE''ANDROOM_IDISNOTNULLORSCHEDULE_TYPEINN''ONLINE'',N''UNASSIGNED''ANDROOM_IDISNULL',
       N'SCHEDULE_TYPEISNOTNULLANDSCHEDULE_TYPE=N''ONSITE''ANDROOM_IDISNOTNULLORSCHEDULE_TYPEINN''ONLINE'',N''UNASSIGNED''ANDROOM_IDISNULL',
       N'SCHEDULE_TYPE=N''ONSITE''ANDROOM_IDISNOTNULLORSCHEDULE_TYPE=N''UNASSIGNED''ORSCHEDULE_TYPE=N''ONLINE''ANDROOM_IDISNULL'
   )
BEGIN
    ;THROW 51012, 'The existing schedule-type check has an unknown definition; review it before migrating.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM dbo.grading_scales
    WHERE minimum_score IS NULL
       OR maximum_score IS NULL
       OR minimum_score <> 0
       OR maximum_score <> 10
)
BEGIN
    ;THROW 51004, 'A grading scale is outside the configured 0-10 range.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM dbo.grading_scale_rules
    WHERE minimum_score IS NULL
       OR maximum_score IS NULL
       OR minimum_score < 0
       OR maximum_score > 10
       OR minimum_score >= maximum_score
)
BEGIN
    ;THROW 51005, 'A grading-scale rule has an invalid score range; review the rows before migrating.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM dbo.course_section_schedules
    WHERE weekday IS NULL
       OR weekday NOT BETWEEN 1 AND 7
       OR start_time IS NULL
       OR end_time IS NULL
       OR start_time >= end_time
       OR schedule_type IS NULL
       OR schedule_type NOT IN (N'ONSITE', N'ONLINE', N'UNASSIGNED')
       OR (schedule_type = N'ONSITE' AND room_id IS NULL)
       OR (schedule_type IN (N'ONLINE', N'UNASSIGNED') AND room_id IS NOT NULL)
)
BEGIN
    ;THROW 51006, 'Invalid schedule rows exist. Review them before requiring a weekday and valid time on every row.', 1;
END;

IF EXISTS (
    SELECT course_section_id
    FROM dbo.lecturer_course_sections
    WHERE deleted_at IS NULL
      AND is_active = 1
    GROUP BY course_section_id
    HAVING COUNT_BIG(*) > 1
)
BEGIN
    ;THROW 51007, 'A course section has multiple active lecturer assignments; resolve duplicates before migrating.', 1;
END;

SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.grading_scales')
          AND name = N'CK_grading_scales_score_range_0_10'
    )
    BEGIN
        ALTER TABLE dbo.grading_scales WITH CHECK
            ADD CONSTRAINT CK_grading_scales_score_range_0_10
            CHECK (
                minimum_score IS NOT NULL
                AND maximum_score IS NOT NULL
                AND minimum_score = 0
                AND maximum_score = 10
            );
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.grading_scale_rules')
          AND name = N'CK_grading_scale_rules_score_range_0_10'
    )
    BEGIN
        ALTER TABLE dbo.grading_scale_rules WITH CHECK
            ADD CONSTRAINT CK_grading_scale_rules_score_range_0_10
            CHECK (
                minimum_score IS NOT NULL
                AND maximum_score IS NOT NULL
                AND minimum_score >= 0
                AND maximum_score <= 10
                AND minimum_score < maximum_score
            );
    END;

    IF EXISTS (
        SELECT 1
        FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.course_section_schedules')
          AND name = N'CK_course_section_schedules_time'
    )
    BEGIN
        ALTER TABLE dbo.course_section_schedules
            DROP CONSTRAINT CK_course_section_schedules_time;
    END;

    ALTER TABLE dbo.course_section_schedules WITH CHECK
        ADD CONSTRAINT CK_course_section_schedules_time
        CHECK (
            weekday IS NOT NULL
            AND weekday >= 1
            AND weekday <= 7
            AND start_time IS NOT NULL
            AND end_time IS NOT NULL
            AND start_time < end_time
        );

    IF EXISTS (
        SELECT 1
        FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'dbo.course_section_schedules')
          AND name = N'CK_course_section_schedules_type'
    )
    BEGIN
        ALTER TABLE dbo.course_section_schedules
            DROP CONSTRAINT CK_course_section_schedules_type;
    END;

    ALTER TABLE dbo.course_section_schedules WITH CHECK
        ADD CONSTRAINT CK_course_section_schedules_type
        CHECK (
            schedule_type IS NOT NULL
            AND (
                (schedule_type = N'ONSITE' AND room_id IS NOT NULL)
                OR (schedule_type IN (N'ONLINE', N'UNASSIGNED') AND room_id IS NULL)
            )
        );

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.lecturer_course_sections')
          AND name = N'UX_lecturer_course_sections_active'
    )
    BEGIN
        CREATE UNIQUE INDEX UX_lecturer_course_sections_active
            ON dbo.lecturer_course_sections(course_section_id)
            WHERE deleted_at IS NULL AND is_active = 1;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.registration_final_grades')
          AND name = N'IX_registration_final_grades_section_scale'
    )
    BEGIN
        CREATE INDEX IX_registration_final_grades_section_scale
            ON dbo.registration_final_grades(course_section_id, grading_scale_id);
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.registration_final_grades')
          AND name = N'IX_registration_final_grades_rule_scale'
    )
    BEGIN
        CREATE INDEX IX_registration_final_grades_rule_scale
            ON dbo.registration_final_grades(grading_scale_rule_id, grading_scale_id);
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.registration_component_grades')
          AND name = N'IX_registration_component_grades_registration_section'
    )
    BEGIN
        CREATE INDEX IX_registration_component_grades_registration_section
            ON dbo.registration_component_grades(course_registration_id, course_section_id);
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.registration_component_grades')
          AND name = N'IX_registration_component_grades_component_section'
    )
    BEGIN
        CREATE INDEX IX_registration_component_grades_component_section
            ON dbo.registration_component_grades(grade_component_id, course_section_id);
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.exam_results')
          AND name = N'IX_exam_results_exam_section'
    )
    BEGIN
        CREATE INDEX IX_exam_results_exam_section
            ON dbo.exam_results(exam_id, course_section_id);
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.exam_results')
          AND name = N'IX_exam_results_registration_section'
    )
    BEGIN
        CREATE INDEX IX_exam_results_registration_section
            ON dbo.exam_results(course_registration_id, course_section_id);
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT
    name AS check_constraint,
    is_disabled,
    is_not_trusted
FROM sys.check_constraints
WHERE parent_object_id IN (
    OBJECT_ID(N'dbo.grading_scales'),
    OBJECT_ID(N'dbo.grading_scale_rules'),
    OBJECT_ID(N'dbo.course_section_schedules')
)
AND name IN (
    N'CK_grading_scales_score_range_0_10',
    N'CK_grading_scale_rules_score_range_0_10',
    N'CK_course_section_schedules_time',
    N'CK_course_section_schedules_type'
)
ORDER BY name;

SELECT
    OBJECT_NAME(object_id) AS table_name,
    name AS index_name,
    is_unique,
    filter_definition
FROM sys.indexes
WHERE name IN (
    N'UX_lecturer_course_sections_active',
    N'IX_registration_final_grades_section_scale',
    N'IX_registration_final_grades_rule_scale',
    N'IX_registration_component_grades_registration_section',
    N'IX_registration_component_grades_component_section',
    N'IX_exam_results_exam_section',
    N'IX_exam_results_registration_section'
)
ORDER BY table_name, index_name;
