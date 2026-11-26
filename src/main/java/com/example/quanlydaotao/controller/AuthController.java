package com.example.quanlydaotao.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.service.JwtTokenService;
import com.example.quanlydaotao.service.JwtTokenService.AuthenticatedUser;
import com.example.quanlydaotao.service.JwtTokenService.SessionTokens;
import com.example.quanlydaotao.service.PasswordHasher;
import com.example.quanlydaotao.service.UserAccountProvisioningService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final JwtTokenService sessions;
    private final PasswordHasher passwordHasher;
    private final UserAccountProvisioningService accountProvisioningService;
    private final Map<String, String> resetTokens = new java.util.concurrent.ConcurrentHashMap<>();

    public AuthController(
            JdbcTemplate jdbcTemplate,
            JwtTokenService sessions,
            PasswordHasher passwordHasher,
            UserAccountProvisioningService accountProvisioningService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.accountProvisioningService = accountProvisioningService;
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
                "roles", user.roles()
        ));
    }

    @PostMapping("/accounts")
    @Transactional
    public ResponseEntity<Map<String, Object>> provisionAccount(
            @RequestBody Map<String, Object> request,
            @RequestAttribute("authenticatedUser") AuthenticatedUser administrator) {
        String username = textValue(request.get("username"));
        String displayName = textValue(request.get("displayName"));
        String password = textValue(request.get("password"));
        List<String> roles = roleValues(request.get("roles"));
        if (username == null || username.length() < 3 || username.length() > 100
                || displayName == null || displayName.length() > 255
                || password == null || password.length() < 12 || password.length() > 72
                    || roles.isEmpty()
                    || request.containsKey("email")) {
            return ResponseEntity.badRequest().body(Map.of(
                        "message", "Username, displayName, mật khẩu hợp lệ và ít nhất một role là bắt buộc; email được lưu trong hồ sơ sinh viên hoặc nhân viên."));
        }
        if (roles.stream().anyMatch(role -> List.of("STUDENT", "LECTURER").contains(role))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Tài khoản STUDENT và LECTURER phải được tạo cùng hồ sơ tương ứng."));
        }

        UUID userId = accountProvisioningService.createAccount(username, displayName, password, roles);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", userId,
                "username", username,
                "displayName", displayName,
                "roles", roles));
    }

    @GetMapping("/accounts")
    public List<Map<String, Object>> getAccounts() {
        List<AccountQueryRow> rows = jdbcTemplate.query("""
                SELECT account.id, account.username, account.display_name,
                       COALESCE(student.email, employee.email) AS email,
                       student.id AS student_id, lecturer.id AS lecturer_id,
                       account.is_active, assigned.code AS role_code
                FROM users account
                LEFT JOIN students student
                  ON student.user_id = account.id
                 AND student.deleted_at IS NULL
                LEFT JOIN employees employee
                  ON employee.user_id = account.id
                 AND employee.deleted_at IS NULL
                LEFT JOIN lecturers lecturer
                  ON lecturer.id = employee.id
                 AND lecturer.deleted_at IS NULL
                LEFT JOIN user_roles assignedRole ON assignedRole.user_id = account.id
                LEFT JOIN roles assigned ON assigned.id = assignedRole.role_id
                ORDER BY account.username, assigned.code
                """, (rs, rowNum) -> new AccountQueryRow(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getObject("student_id", UUID.class),
                rs.getObject("lecturer_id", UUID.class),
                rs.getBoolean("is_active"),
                rs.getString("role_code")));

        Map<UUID, Map<String, Object>> accounts = new LinkedHashMap<>();
        Map<UUID, LinkedHashSet<String>> rolesByAccount = new LinkedHashMap<>();
        for (AccountQueryRow row : rows) {
            accounts.computeIfAbsent(row.id(), id -> {
                Map<String, Object> account = new LinkedHashMap<>();
                account.put("id", id);
                account.put("username", row.username());
                account.put("fullName", row.displayName() == null ? row.username() : row.displayName());
                account.put("email", row.email());
                account.put("studentId", row.studentId());
                account.put("lecturerId", row.lecturerId());
                account.put("isActive", row.isActive());
                return account;
            });
            if (row.roleCode() != null) {
                rolesByAccount.computeIfAbsent(row.id(), id -> new LinkedHashSet<>())
                        .add(normalizeRole(row.roleCode()));
            }
        }
        accounts.forEach((id, account) ->
                account.put("roles", String.join(",", rolesByAccount.getOrDefault(id, new LinkedHashSet<>()))));
        return new ArrayList<>(accounts.values());
    }

    @GetMapping("/roles")
    public List<String> getActiveRoles() {
        return jdbcTemplate.query("""
                SELECT code
                FROM roles
                WHERE is_active = 1
                ORDER BY code
                """, (rs, rowNum) -> normalizeRole(rs.getString("code")));
    }

    @PatchMapping("/accounts/{id}/status")
    public ResponseEntity<Map<String, String>> updateAccountStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        Object activeValue = request.get("active");
        if (!(activeValue instanceof Boolean active)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Trạng thái active phải là true hoặc false."));
        }
        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
        if (accountCount == null || accountCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản.");
        }
        if (!active && isActiveAdministrator(id)) {
            Integer otherActiveAdmins = jdbcTemplate.queryForObject("""
                    SELECT COUNT(DISTINCT account.id)
                    FROM users account
                    JOIN user_roles assignedRole ON assignedRole.user_id = account.id
                    JOIN roles assigned ON assigned.id = assignedRole.role_id
                    WHERE account.is_active = 1
                      AND assigned.is_active = 1
                      AND assigned.code = N'ADMIN'
                      AND account.id <> ?
                    """, Integer.class, id);
            if (otherActiveAdmins == null || otherActiveAdmins == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Không thể khóa ADMIN cuối cùng đang hoạt động.");
            }
        }
        jdbcTemplate.update("""
                UPDATE users
                SET is_active = ?, updated_at = ?
                WHERE id = ?
                """, active, java.sql.Timestamp.from(java.time.Instant.now()), id);
        return ResponseEntity.ok(Map.of("message", "Đã cập nhật trạng thái tài khoản."));
    }

    @PatchMapping("/accounts/{id}/roles")
    @Transactional
    public ResponseEntity<Map<String, String>> updateAccountRoles(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        List<String> roles = roleValues(request.get("roles"));
        if (roles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phải chọn ít nhất một role."));
        }
        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
        if (accountCount == null || accountCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản.");
        }

        boolean hasStudentProfile = hasProfile("students", "user_id", id);
        boolean hasLecturerProfile = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM employees employee
                JOIN lecturers lecturer ON lecturer.id = employee.id
                WHERE employee.user_id = ?
                  AND employee.deleted_at IS NULL
                  AND lecturer.deleted_at IS NULL
                """, Integer.class, id) > 0;
        if ((hasStudentProfile && !roles.contains("STUDENT"))
                || (hasLecturerProfile && !roles.contains("LECTURER"))
                || (roles.contains("STUDENT") && !hasStudentProfile)
                || (roles.contains("LECTURER") && !hasLecturerProfile)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Role STUDENT/LECTURER phải khớp với hồ sơ tương ứng."));
        }
        if (!roles.contains("ADMIN") && isActiveAdministrator(id)) {
            Integer otherActiveAdmins = jdbcTemplate.queryForObject("""
                    SELECT COUNT(DISTINCT account.id)
                    FROM users account
                    JOIN user_roles assignedRole ON assignedRole.user_id = account.id
                    JOIN roles assigned ON assigned.id = assignedRole.role_id
                    WHERE account.is_active = 1
                      AND assigned.is_active = 1
                      AND assigned.code = N'ADMIN'
                      AND account.id <> ?
                    """, Integer.class, id);
            if (otherActiveAdmins == null || otherActiveAdmins == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Không thể gỡ role ADMIN khỏi ADMIN cuối cùng đang hoạt động.");
            }
        }

        List<UUID> roleIds = new ArrayList<>();
        for (String role : roles) {
            List<UUID> matches = jdbcTemplate.query("""
                    SELECT id
                    FROM roles
                    WHERE code = ?
                      AND is_active = 1
                    """, (rs, rowNum) -> rs.getObject("id", UUID.class), role);
            if (matches.size() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role không tồn tại hoặc đã bị khóa.");
            }
            roleIds.add(matches.get(0));
        }

        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", id);
        for (UUID roleId : roleIds) {
            jdbcTemplate.update("""
                    INSERT INTO user_roles (id, user_id, role_id)
                    VALUES (?, ?, ?)
                    """, UUID.randomUUID(), id, roleId);
        }
        return ResponseEntity.ok(Map.of("message", "Đã cập nhật phân quyền tài khoản."));
    }

    @PostMapping("/accounts/{id}/password")
    public ResponseEntity<Map<String, String>> resetAccountPassword(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        String password = request.getOrDefault("password", "");
        if (password.length() < 12 || password.length() > 72) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Mật khẩu cần từ 12 đến 72 ký tự."));
        }
        int updated = jdbcTemplate.update("""
                UPDATE users
                SET password_hash = ?, updated_at = ?
                WHERE id = ?
                """, passwordHasher.hash(password), java.sql.Timestamp.from(java.time.Instant.now()), id);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản.");
        }
        return ResponseEntity.ok(Map.of("message", "Đã đổi mật khẩu tài khoản."));
    }

    @GetMapping("/independent-roles")
    public List<String> independentAccountRoles() {
        return jdbcTemplate.query("""
                SELECT code
                FROM roles
                WHERE is_active = 1
                  AND code NOT IN (N'STUDENT', N'LECTURER')
                ORDER BY code
                """, (rs, rowNum) -> normalizeRole(rs.getString("code")));
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
        List<UUID> accounts = jdbcTemplate.query("""
                SELECT id
                FROM users
                WHERE username = ?
                   OR id IN (
                       SELECT student.user_id
                       FROM students student
                       WHERE student.email = ?
                         AND student.user_id IS NOT NULL
                       UNION
                       SELECT employee.user_id
                       FROM employees employee
                       WHERE employee.email = ?
                         AND employee.user_id IS NOT NULL
                   )
                """, (rs, rowNum) -> rs.getObject("id", UUID.class),
                identifier, identifier, identifier);
        if (accounts.size() != 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Không tìm thấy tài khoản duy nhất tương ứng với thông tin đặt lại."));
        }
        String passwordHash = passwordHasher.hash(password);
        int updated = jdbcTemplate.update("""
                UPDATE users
                SET password_hash = ?, updated_at = ?
                WHERE id = ?
                """, passwordHash, java.sql.Timestamp.from(java.time.Instant.now()), accounts.get(0));
        if (updated != 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Không thể cập nhật mật khẩu cho tài khoản."));
        }
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công."));
    }

    private AuthenticatedUser findDatabaseUser(String username, String password) {
        List<DatabaseUser> rows = jdbcTemplate.query("""
                SELECT TOP (1) account.id, account.username, account.password_hash,
                       account.display_name,
                       COALESCE(student.code, employee.employee_code) AS profile_code
                FROM users account
                LEFT JOIN students student ON student.user_id = account.id
                LEFT JOIN employees employee ON employee.user_id = account.id
                WHERE account.is_active = 1
                  AND (account.username = ?
                    OR student.email = ?
                    OR employee.email = ?
                    OR student.code = ?
                    OR employee.employee_code = ?)
                """, (rs, rowNum) -> new DatabaseUser(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("profile_code")), username, username, username, username, username);
        if (rows.isEmpty()) {
            return null;
        }
        DatabaseUser row = rows.get(0);
        List<String> roles = loadRoleCodes(row.id());
        if (roles.isEmpty() || !passwordHasher.matches(password, row.passwordHash())) {
            return null;
        }
        return new AuthenticatedUser(
                row.id(),
                row.username(),
                row.displayName() == null ? row.username() : row.displayName(),
                roles,
                row.profileCode()
        );
    }

    private List<String> loadRoleCodes(String userId) {
        return jdbcTemplate.query("""
                SELECT r.code
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND r.is_active = 1
                ORDER BY r.code
                """, (rs, rowNum) -> normalizeRole(rs.getString("code")), userId);
    }

    private String normalizeRole(String role) {
        return role.toUpperCase(Locale.ROOT);
    }

    private boolean hasProfile(String table, String userIdColumn, UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + userIdColumn + " = ? AND deleted_at IS NULL",
                Integer.class, id);
        return count != null && count > 0;
    }

    private boolean isActiveAdministrator(UUID id) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM users account
                JOIN user_roles assignedRole ON assignedRole.user_id = account.id
                JOIN roles assigned ON assigned.id = assignedRole.role_id
                WHERE account.id = ?
                  AND account.is_active = 1
                  AND assigned.is_active = 1
                  AND assigned.code = N'ADMIN'
                """, Integer.class, id);
        return count != null && count > 0;
    }

    private record AccountQueryRow(
            UUID id,
            String username,
            String displayName,
            String email,
            UUID studentId,
            UUID lecturerId,
            boolean isActive,
            String roleCode) {
    }

    private String textValue(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private List<String> roleValues(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(this::normalizeRole)
                .distinct()
                .toList();
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
                        "roles", user.roles()
                ));
    }

    private record DatabaseUser(
            String id,
            String username,
            String passwordHash,
            String displayName,
            String profileCode) {
    }
}
