package com.example.quanlydaotao.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialAdminBootstrapService {

    private final JdbcTemplate jdbcTemplate;
    private final UserAccountProvisioningService accountProvisioningService;

    public InitialAdminBootstrapService(
            JdbcTemplate jdbcTemplate,
            UserAccountProvisioningService accountProvisioningService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountProvisioningService = accountProvisioningService;
    }

    @Transactional
    public boolean createFirstAdmin(String username, String displayName, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 100
                || normalizedDisplayName.isEmpty() || normalizedDisplayName.length() > 255
                || password == null || password.length() < 12 || password.length() > 72) {
            throw new IllegalArgumentException(
                    "Bootstrap username, display name, or password does not meet the required length.");
        }

        Integer activeAdminCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM users account
                JOIN user_roles assigned ON assigned.user_id = account.id
                JOIN roles role ON role.id = assigned.role_id
                WHERE account.is_active = 1
                  AND role.code = 'ADMIN'
                  AND role.is_active = 1
                """, Integer.class);
        if (activeAdminCount != null && activeAdminCount > 0) {
            return false;
        }

        accountProvisioningService.createAccount(
                normalizedUsername, normalizedDisplayName, password, List.of("ADMIN"));
        return true;
    }
}
