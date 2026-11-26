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
    public UUID createAccount(String username, String displayName, String password, List<String> requestedRoles) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 100
                || normalizedDisplayName.isEmpty() || normalizedDisplayName.length() > 255
                || password == null || password.length() < 12 || password.length() > 72
                || requestedRoles == null || requestedRoles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Tên đăng nhập, họ tên, mật khẩu và role hợp lệ là bắt buộc");
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
                    (id, username, password_hash, display_name, is_active, created_at)
                VALUES (?, ?, ?, ?, 1, ?)
                """, userId, normalizedUsername, passwordHasher.hash(password), normalizedDisplayName,
                Timestamp.from(Instant.now()));
        for (UUID roleId : roleIds) {
            jdbcTemplate.update("""
                    INSERT INTO user_roles (id, user_id, role_id)
                    VALUES (?, ?, ?)
                    """, UUID.randomUUID(), userId, roleId);
        }
        return userId;
    }
}
