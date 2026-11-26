package com.example.quanlydaotao.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.example.quanlydaotao.entity.Student;
import com.example.quanlydaotao.repository.StudentRepository;
import com.example.quanlydaotao.service.JwtTokenService;
import com.example.quanlydaotao.service.JwtTokenService.AuthenticatedUser;
import com.example.quanlydaotao.service.JwtTokenService.SessionTokens;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final StudentRepository studentRepository;
    private final JwtTokenService sessions;
    private final Map<String, String> resetTokens = new java.util.concurrent.ConcurrentHashMap<>();

    public AuthController(
            JdbcTemplate jdbcTemplate,
            StudentRepository studentRepository,
            JwtTokenService sessions,
            @Value("${app.auth.initialize-schema:true}") boolean initializeSchema,
            @Value("${app.auth.initial-admin-password}") String initialAdminPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.studentRepository = studentRepository;
        this.sessions = sessions;
        if (initializeSchema) {
            ensureAccountsSchema(initialAdminPassword);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.getOrDefault("username", "").trim();
        String password = request.getOrDefault("password", "");

        AuthenticatedUser user = findDatabaseUser(username, password);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Tên đăng nhập hoặc mật khẩu không đúng."));
        }

        return ResponseEntity.ok(sessionResponse(user, sessions.createSession(user)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody(required = false) Map<String, String> request) {
        SessionTokens tokens = sessions.rotateRefreshToken(request == null ? null : request.get("refreshToken"));
        if (tokens == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh token không hợp lệ, đã hết hạn hoặc đã được sử dụng."));
        }
        AuthenticatedUser user = sessions.findByAuthorization("Bearer " + tokens.accessToken());
        return ResponseEntity.ok(sessionResponse(user, tokens));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Thiếu thông tin đăng ký."));
        }
        String username = value(request, "username").trim();
        String fullName = value(request, "fullName").trim();
        String email = value(request, "email").trim();
        String studentCode = value(request, "studentCode").trim();
        String password = value(request, "password");

        if (username.length() < 3 || username.length() > 100
                || fullName.isBlank() || email.isBlank() || studentCode.isBlank()
                || !email.contains("@") || password.length() < 12 || password.length() > 72) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Thông tin không hợp lệ; mật khẩu cần từ 12 đến 72 ký tự."));
        }

        Student student = studentRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(studentCode)
                .filter(candidate -> !Boolean.FALSE.equals(candidate.getIsActive()))
                .orElse(null);
        if (student == null
                || !fullName.equalsIgnoreCase(student.getFullName() == null ? "" : student.getFullName().trim())
                || !StringUtils.hasText(student.getEmail())
                || !email.equalsIgnoreCase(student.getEmail().trim())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Không tìm thấy hồ sơ sinh viên khớp với mã, họ tên và email."));
        }

        try {
            Integer duplicateCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM users
                    WHERE LOWER(username) = LOWER(?)
                       OR LOWER(email) = LOWER(?)
                       OR LOWER(student_code) = LOWER(?)
                    """, Integer.class, username, email, studentCode);
            if (duplicateCount != null && duplicateCount > 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Tên đăng nhập, email hoặc mã sinh viên đã được sử dụng."));
            }
            jdbcTemplate.update("""
                    INSERT INTO users
                        (id, username, password_hash, full_name, email, student_code, role_name, is_active, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'STUDENT', 1, CURRENT_TIMESTAMP)
                    """,
                    UUID.randomUUID(), username, hashPassword(password), student.getFullName(),
                    email, student.getCode());
        } catch (DataAccessException e) {
            throw new IllegalStateException("Không thể tạo tài khoản sinh viên.", e);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Đăng ký tài khoản thành công. Vui lòng đăng nhập."));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthenticatedUser user = sessions.findByAuthorization(authorization);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Phiên đăng nhập không hợp lệ."));
        }
        return ResponseEntity.ok(Map.of(
                "id", user.id(),
                "username", user.username(),
                "fullName", user.fullName(),
                "roles", List.of(user.role())
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) Map<String, String> request) {
        if (request != null) {
            sessions.revokeRefreshToken(request.get("refreshToken"));
        }
        return ResponseEntity.ok(Map.of("message", "Đã đăng xuất."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        String identifier = request.getOrDefault("identifier", "").trim();
        String resetToken = UUID.randomUUID().toString();
        resetTokens.put(resetToken, identifier);
        return ResponseEntity.ok(Map.of(
                "message", "Mã đặt lại mật khẩu đã được tạo.",
                "resetToken", resetToken
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.getOrDefault("token", "");
        String password = request.getOrDefault("password", "");
        String identifier = resetTokens.remove(token);
        if (identifier == null || password.length() < 12 || password.length() > 72) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Mã đặt lại không hợp lệ hoặc mật khẩu cần từ 12 đến 72 ký tự."));
        }
        String passwordHash = hashPassword(password);
        jdbcTemplate.update("""
                UPDATE dbo.users
                SET password_hash = ?, updated_at = SYSUTCDATETIME()
                WHERE username = ? OR email = ? OR student_code = ? OR lecturer_code = ?
                """, passwordHash, identifier, identifier, identifier, identifier);
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công."));
    }

    private AuthenticatedUser findDatabaseUser(String username, String password) {
        List<DatabaseUser> rows = jdbcTemplate.query("""
                SELECT id, username, password_hash, full_name, role_name, student_code
                FROM users
                WHERE is_active = 1
                  AND (LOWER(username) = LOWER(?)
                    OR LOWER(email) = LOWER(?)
                    OR LOWER(student_code) = LOWER(?)
                    OR LOWER(lecturer_code) = LOWER(?))
                """, (rs, rowNum) -> new DatabaseUser(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                rs.getString("role_name"),
                rs.getString("student_code")), username, username, username, username);
        if (rows.isEmpty()) {
            return null;
        }
        DatabaseUser row = rows.get(0);
        if (!verifyPassword(password, row.passwordHash())) {
            return null;
        }
        return new AuthenticatedUser(
                row.id() == null ? UUID.randomUUID().toString() : row.id(),
                row.username(),
                row.fullName() == null ? row.username() : row.fullName(),
                normalizeRole(row.role()),
                row.studentCode()
        );
    }

    private void ensureAccountsSchema(String initialAdminPassword) {
        jdbcTemplate.execute("""
                    IF OBJECT_ID(N'dbo.users', N'U') IS NULL
                    BEGIN
                        CREATE TABLE dbo.users (
                            id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
                            username NVARCHAR(100) NOT NULL UNIQUE,
                            password_hash NVARCHAR(255) NOT NULL,
                            full_name NVARCHAR(255) NULL,
                            email NVARCHAR(255) NULL,
                            student_code NVARCHAR(100) NULL,
                            lecturer_code NVARCHAR(100) NULL,
                            role_name NVARCHAR(50) NOT NULL DEFAULT 'STUDENT',
                            is_active BIT NOT NULL DEFAULT 1,
                            created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
                            updated_at DATETIME2 NULL
                        );
                    END;
                    """);
        jdbcTemplate.update("""
                    IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE LOWER(username) = 'admin')
                    BEGIN
                        INSERT INTO dbo.users (username, password_hash, full_name, role_name, is_active)
                        VALUES ('admin', ?, 'Administrator', 'ADMIN', 1);
                    END;
                    """, hashPassword(initialAdminPassword));
    }

    private boolean verifyPassword(String password, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        if (!storedHash.startsWith("pbkdf2$")) {
            return MessageDigest.isEqual(
                    storedHash.getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String[] parts = storedHash.split("\\$", 4);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private String hashPassword(String password) throws DataAccessException {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return "pbkdf2$120000$" + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
            throw new IllegalStateException("Cannot hash password", e);
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "STUDENT";
        }
        return role.toUpperCase();
    }

    private String value(Map<String, String> request, String key) {
        return request.getOrDefault(key, "");
    }

    private Map<String, Object> sessionResponse(AuthenticatedUser user, SessionTokens tokens) {
        return Map.of(
                "accessToken", tokens.accessToken(),
                "refreshToken", tokens.refreshToken(),
                "tokenType", "Bearer",
                "accessTokenExpiresIn", JwtTokenService.ACCESS_TOKEN_TTL_SECONDS,
                "user", Map.of(
                        "id", user.id(),
                        "username", user.username(),
                        "fullName", user.fullName(),
                        "roles", List.of(user.role())
                ));
    }

    private record DatabaseUser(
            String id,
            String username,
            String passwordHash,
            String fullName,
            String role,
            String studentCode) {
    }
}
