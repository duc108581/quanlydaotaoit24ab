package com.example.quanlydaotao;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthJwtApiTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                    full_name VARCHAR(255),
                    email VARCHAR(255),
                    student_code VARCHAR(100),
                    lecturer_code VARCHAR(100),
                    role_name VARCHAR(50) NOT NULL,
                    is_active INTEGER NOT NULL,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
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
    }

    @Test
    void loginIssuesJwtAndRefreshRotatesAndRevokesRefreshTokens() throws Exception {
        String username = "jwt-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users
                    (id, username, password_hash, full_name, email, student_code, role_name, is_active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'STUDENT', 1, CURRENT_TIMESTAMP)
                """, userId, username, "test-password", "JWT Test Student", username + "@example.test", "JWT001");

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

    private HttpResponse<String> loginAsAdmin() throws Exception {
        String username = "admin-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users
                    (id, username, password_hash, full_name, email, role_name, is_active, created_at)
                VALUES (?, ?, ?, ?, ?, 'ADMIN', 1, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), username, "admin-password", "JWT Test Admin", username + "@example.test");
        return send("/api/auth/login", "POST", null, """
                {"username":"%s","password":"admin-password"}
                """.formatted(username));
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
