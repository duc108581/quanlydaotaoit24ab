IF OBJECT_ID(N'dbo.notifications', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.notifications (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL,
        title NVARCHAR(255) NOT NULL,
        content NVARCHAR(MAX) NOT NULL,
        type NVARCHAR(50) NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT SYSUTCDATETIME(),
        read_at DATETIME2 NULL
    );
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_notifications_user_created_at'
      AND object_id = OBJECT_ID(N'dbo.notifications')
)
BEGIN
    CREATE INDEX IX_notifications_user_created_at
        ON dbo.notifications (user_id, created_at DESC);
END;
GO
