package com.example.quanlydaotao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import com.example.quanlydaotao.service.JwtTokenService;
import com.example.quanlydaotao.service.JwtTokenService.AuthenticatedUser;
import com.example.quanlydaotao.service.InitialAdminBootstrapService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthJwtApiTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private InitialAdminBootstrapService adminBootstrapService;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void createSupportTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id UUID PRIMARY KEY,
                    username VARCHAR(100) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    display_name VARCHAR(255),
                    is_active INTEGER NOT NULL,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS roles (
                    id UUID PRIMARY KEY,
                    code VARCHAR(50) NOT NULL UNIQUE,
                    is_active INTEGER NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_roles (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL,
                    role_id UUID NOT NULL,
                    UNIQUE (user_id, role_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS refresh_tokens (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL,
                    token_hash CHAR(64) NOT NULL UNIQUE,
                    expires_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    revoked_at TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                MERGE INTO roles (id, code, is_active)
                KEY (code)
                VALUES (?, 'STUDENT', 1)
                """, UUID.nameUUIDFromBytes("STUDENT".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbcTemplate.update("""
                MERGE INTO roles (id, code, is_active)
                KEY (code)
                VALUES (?, 'ADMIN', 1)
                """, UUID.nameUUIDFromBytes("ADMIN".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbcTemplate.update("""
                MERGE INTO roles (id, code, is_active)
                KEY (code)
                VALUES (?, 'SUPPORT', 1)
                """, UUID.nameUUIDFromBytes("SUPPORT".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbcTemplate.update("""
                MERGE INTO roles (id, code, is_active)
                KEY (code)
                VALUES (?, 'AUDITOR', 1)
                """, UUID.nameUUIDFromBytes("AUDITOR".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void loginIssuesJwtAndRefreshRotatesAndRevokesRefreshTokens() throws Exception {
        String username = "jwt-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users
                    (id, username, password_hash, display_name, is_active, created_at)
                VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                """, userId, username, "test-password", "JWT Test Student");
        assignRole(userId, "STUDENT");

        HttpResponse<String> loginResponse = send("/api/auth/login", "POST", null, """
                {"username":"%s","password":"test-password"}
                """.formatted(username));
        assertEquals(200, loginResponse.statusCode(), loginResponse.body());
        JsonNode login = objectMapper.readTree(loginResponse.body());
        String accessToken = login.path("accessToken").asText();
        String refreshToken = login.path("refreshToken").asText();
        assertEquals(3, accessToken.split("\\.").length);

        HttpResponse<String> meResponse = send(
                "/api/auth/me", "GET", "Bearer " + accessToken, null);
        assertEquals(200, meResponse.statusCode(), meResponse.body());
        assertEquals(userId.toString(), objectMapper.readTree(meResponse.body()).path("id").asText());

        assertEquals(401, send("/api/students", "GET", null, null).statusCode());
        assertEquals(403, send("/api/students", "GET", "Bearer " + accessToken, null).statusCode());

        HttpResponse<String> refreshResponse = send("/api/auth/refresh", "POST", null, """
                {"refreshToken":"%s"}
                """.formatted(refreshToken));
        assertEquals(200, refreshResponse.statusCode(), refreshResponse.body());
        JsonNode refreshed = objectMapper.readTree(refreshResponse.body());
        String rotatedRefreshToken = refreshed.path("refreshToken").asText();
        assertNotEquals(refreshToken, rotatedRefreshToken);
        assertEquals(401, send("/api/auth/refresh", "POST", null, """
                {"refreshToken":"%s"}
                """.formatted(refreshToken)).statusCode());

        HttpResponse<String> adminLogin = loginAsAdmin();
        assertEquals(200, adminLogin.statusCode(), adminLogin.body());
        String adminAccessToken = objectMapper.readTree(adminLogin.body()).path("accessToken").asText();
        assertEquals(200, send("/api/students", "GET", "Bearer " + adminAccessToken, null).statusCode());

        assertEquals(200, send("/api/auth/logout", "POST", null, """
                {"refreshToken":"%s"}
                """.formatted(rotatedRefreshToken)).statusCode());
        assertEquals(401, send("/api/auth/refresh", "POST", null, """
                {"refreshToken":"%s"}
                """.formatted(rotatedRefreshToken)).statusCode());
        assertTrue(login.path("accessTokenExpiresIn").asLong() > 0);
    }

    @Test
    void onlyAdministratorCanProvisionIndependentAccounts() throws Exception {
        String username = "provisioned-" + UUID.randomUUID();
        String requestBody = """
                {"username":"%s","displayName":"Provisioned Support","password":"test-password-123","roles":["SUPPORT"]}
                """.formatted(username);
        String adminToken = jwtTokenService.issueAccessToken(new AuthenticatedUser(
                UUID.randomUUID().toString(), "test-admin", "Test Admin", java.util.List.of("ADMIN"), null));
        String studentToken = jwtTokenService.issueAccessToken(new AuthenticatedUser(
                UUID.randomUUID().toString(), "test-student", "Test Student", java.util.List.of("STUDENT"), null));

        assertEquals(403, send("/api/auth/accounts", "POST", "Bearer " + studentToken, requestBody).statusCode());
        HttpResponse<String> response = send("/api/auth/accounts", "POST", "Bearer " + adminToken, requestBody);
        assertEquals(201, response.statusCode(), response.body());
        UUID userId = UUID.fromString(objectMapper.readTree(response.body()).path("id").asText());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND display_name = 'Provisioned Support'",
                Integer.class, userId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_roles assigned
                JOIN roles role ON role.id = assigned.role_id
                WHERE assigned.user_id = ?
                  AND role.code = 'SUPPORT'
                """, Integer.class, userId));
        assertEquals(400, send("/api/auth/accounts", "POST", "Bearer " + adminToken, """
                {"username":"student-%s","displayName":"Independent Student","password":"test-password-123","roles":["STUDENT"]}
                """.formatted(UUID.randomUUID())).statusCode());
        HttpResponse<String> accountList = send("/api/auth/accounts", "GET", "Bearer " + adminToken, null);
        assertEquals(200, accountList.statusCode(), accountList.body());
        assertTrue(accountList.body().contains(username));
        assertEquals(200, send("/api/auth/accounts/" + userId + "/roles", "PATCH", "Bearer " + adminToken, """
                {"roles":["AUDITOR"]}
                """).statusCode());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_roles assigned
                JOIN roles role ON role.id = assigned.role_id
                WHERE assigned.user_id = ?
                  AND role.code = 'AUDITOR'
                """, Integer.class, userId));
        assertEquals(200, send("/api/auth/accounts/" + userId + "/status", "PATCH", "Bearer " + adminToken, """
                {"active":false}
                """).statusCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Integer.class, userId));
        assertEquals(200, send("/api/auth/accounts/" + userId + "/status", "PATCH", "Bearer " + adminToken, """
                {"active":true}
                """).statusCode());
        assertEquals(200, send("/api/auth/accounts/" + userId + "/password", "POST", "Bearer " + adminToken, """
                {"password":"another-password-123"}
                """).statusCode());
        assertEquals(200, send("/api/auth/login", "POST", null, """
                {"username":"%s","password":"another-password-123"}
                """.formatted(username)).statusCode());
    }

    @Test
    void passwordResetReportsSuccessOnlyAfterUpdatingTheAccount() throws Exception {
        String username = "reset-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users
                    (id, username, password_hash, display_name, is_active, created_at)
                VALUES (?, ?, 'old-password', 'Reset Test', 1, CURRENT_TIMESTAMP)
                """, userId, username);
        assignRole(userId, "STUDENT");

        HttpResponse<String> resetRequest = send("/api/auth/forgot-password", "POST", null, """
                {"identifier":"%s"}
                """.formatted(username));
        assertEquals(200, resetRequest.statusCode());
        String resetToken = objectMapper.readTree(resetRequest.body()).path("resetToken").asText();
        HttpResponse<String> resetResponse = send("/api/auth/reset-password", "POST", null, """
                {"token":"%s","password":"new-password-long-enough"}
                """.formatted(resetToken));
        assertEquals(200, resetResponse.statusCode(), resetResponse.body());

        assertEquals(200, send("/api/auth/login", "POST", null, """
                {"username":"%s","password":"new-password-long-enough"}
                """.formatted(username)).statusCode());
        assertEquals(401, send("/api/auth/login", "POST", null, """
                {"username":"%s","password":"old-password"}
                """.formatted(username)).statusCode());

        HttpResponse<String> unknownResetRequest = send("/api/auth/forgot-password", "POST", null, """
                {"identifier":"unknown-%s"}
                """.formatted(UUID.randomUUID()));
        String unknownResetToken = objectMapper.readTree(unknownResetRequest.body()).path("resetToken").asText();
        assertEquals(400, send("/api/auth/reset-password", "POST", null, """
                {"token":"%s","password":"another-new-password"}
                """.formatted(unknownResetToken)).statusCode());
    }

    @Test
    void firstAdminBootstrapPersistsAccountAndIsIdempotent() throws Exception {
        String username = "first-admin-" + UUID.randomUUID();
        assertTrue(adminBootstrapService.createFirstAdmin(
                username, "First Admin", "a-strong-password-123"));
        assertFalse(adminBootstrapService.createFirstAdmin(
                username, "Changed Name", "a-different-password-456"));

        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM users account
                JOIN user_roles assigned ON assigned.user_id = account.id
                JOIN roles role ON role.id = assigned.role_id
                WHERE account.username = ?
                  AND account.is_active = 1
                  AND role.code = 'ADMIN'
                  AND role.is_active = 1
                """, Integer.class, username));
        assertEquals(200, send("/api/auth/login", "POST", null, """
                {"username":"%s","password":"a-strong-password-123"}
                """.formatted(username)).statusCode());
    }

    private HttpResponse<String> loginAsAdmin() throws Exception {
        String username = "admin-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users
                    (id, username, password_hash, display_name, is_active, created_at)
                VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), username, "admin-password", "JWT Test Admin");
        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
        assignRole(userId, "ADMIN");
        return send("/api/auth/login", "POST", null, """
                {"username":"%s","password":"admin-password"}
                """.formatted(username));
    }

    private void assignRole(UUID userId, String roleCode) {
        UUID roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", UUID.class, roleCode);
        jdbcTemplate.update(
                "INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), userId, roleId);
    }

    private HttpResponse<String> send(String path, String method, String authorization, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
