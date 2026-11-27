package com.example.quanlydaotao.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAccountProvisioningService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;

    public UserAccountProvisioningService(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public UUID createAccount(
            String username, String displayName, String email, String password, List<String> requestedRoles) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        String normalizedEmail = normalizeEmail(email);
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 100
                || normalizedDisplayName.isEmpty() || normalizedDisplayName.length() > 255
                || (normalizedEmail != null && (normalizedEmail.length() > 254
                        || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
                || password == null || password.length() < 12 || password.length() > 72
                || requestedRoles == null || requestedRoles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Tên đăng nhập, email, họ tên, mật khẩu và role hợp lệ là bắt buộc");
        }

        List<String> roleCodes = requestedRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (roleCodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phải chọn ít nhất một role");
        }

        Integer duplicateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, normalizedUsername);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã được sử dụng");
        }
        if (normalizedEmail != null) {
            Integer duplicateEmailCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE LOWER(email) = ?",
                    Integer.class, normalizedEmail);
            if (duplicateEmailCount != null && duplicateEmailCount > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng");
            }
        }

        List<UUID> roleIds = new ArrayList<>();
        for (String roleCode : roleCodes) {
            List<UUID> matches = jdbcTemplate.query("""
                    SELECT id
                    FROM roles
                    WHERE code = ?
                      AND is_active = 1
                    """, (rs, rowNum) -> rs.getObject("id", UUID.class), roleCode);
            if (matches.size() != 1) {
                throw new IllegalStateException("Role không tồn tại hoặc không hoạt động: " + roleCode);
            }
            roleIds.add(matches.get(0));
        }

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users
                    (id, username, password_hash, display_name, email, is_active, created_at)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """, userId, normalizedUsername, passwordHasher.hash(password), normalizedDisplayName,
                normalizedEmail,
                Timestamp.from(Instant.now()));
        for (UUID roleId : roleIds) {
            jdbcTemplate.update("""
                    INSERT INTO user_roles (id, user_id, role_id)
                    VALUES (?, ?, ?)
                    """, UUID.randomUUID(), userId, roleId);
        }
        return userId;
    }

    @Transactional
    public void updateAccountEmail(UUID userId, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && (normalizedEmail.length() > 254
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        }
        if (normalizedEmail != null) {
            Integer duplicateCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE LOWER(email) = ? AND id <> ?",
                    Integer.class, normalizedEmail, userId);
            if (duplicateCount != null && duplicateCount > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng");
            }
        }
        int updated = jdbcTemplate.update("""
                UPDATE users
                SET email = ?, updated_at = ?
                WHERE id = ?
                """, normalizedEmail, Timestamp.from(Instant.now()), userId);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản liên kết hồ sơ");
        }
        jdbcTemplate.update("UPDATE students SET email = ? WHERE user_id = ?", normalizedEmail, userId);
        jdbcTemplate.update("UPDATE employees SET email = ? WHERE user_id = ?", normalizedEmail, userId);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
