IF OBJECT_ID(N'dbo.users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_users PRIMARY KEY DEFAULT NEWID(),
        username NVARCHAR(100) NOT NULL CONSTRAINT UQ_users_username UNIQUE,
        password_hash NVARCHAR(255) NOT NULL,
        full_name NVARCHAR(255) NULL,
        email NVARCHAR(255) NULL,
        student_code NVARCHAR(100) NULL,
        lecturer_code NVARCHAR(100) NULL,
        role_name NVARCHAR(50) NOT NULL CONSTRAINT DF_users_role DEFAULT 'STUDENT',
        is_active BIT NOT NULL CONSTRAINT DF_users_active DEFAULT 1,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_users_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL
    );
END;
GO

IF OBJECT_ID(N'dbo.refresh_tokens', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.refresh_tokens (
        id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_refresh_tokens PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL,
        token_hash CHAR(64) NOT NULL,
        expires_at DATETIME2 NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_refresh_tokens_created_at DEFAULT SYSUTCDATETIME(),
        revoked_at DATETIME2 NULL,
        CONSTRAINT FK_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UX_refresh_tokens_token_hash'
      AND object_id = OBJECT_ID(N'dbo.refresh_tokens')
)
BEGIN
    CREATE UNIQUE INDEX UX_refresh_tokens_token_hash
        ON dbo.refresh_tokens (token_hash);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_refresh_tokens_user_expiry'
      AND object_id = OBJECT_ID(N'dbo.refresh_tokens')
)
BEGIN
    CREATE INDEX IX_refresh_tokens_user_expiry
        ON dbo.refresh_tokens (user_id, expires_at);
END;
GO
