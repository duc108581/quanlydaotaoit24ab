package com.example.quanlydaotao.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Service
public class JwtTokenService {

    public static final long ACCESS_TOKEN_TTL_SECONDS = 900;
    private static final int REFRESH_TOKEN_TTL_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes.");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Transactional
    public SessionTokens createSession(AuthenticatedUser user) {
        String refreshToken = generateRefreshToken();
        storeRefreshToken(user.id(), refreshToken);
        return new SessionTokens(issueAccessToken(user), refreshToken);
    }

    @Transactional
    public SessionTokens rotateRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }

        String tokenHash = hashRefreshToken(refreshToken);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<RefreshUser> users = jdbcTemplate.query("""
                SELECT u.id, u.username, u.display_name,
                       COALESCE(student.code, employee.employee_code) AS profile_code
                FROM refresh_tokens rt
                JOIN users u ON u.id = rt.user_id
                LEFT JOIN students student ON student.user_id = u.id
                LEFT JOIN employees employee ON employee.user_id = u.id
                WHERE rt.token_hash = ?
                  AND rt.revoked_at IS NULL
                  AND rt.expires_at > ?
                  AND u.is_active = 1
                """, (rs, rowNum) -> new RefreshUser(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("profile_code")), tokenHash, java.sql.Timestamp.valueOf(now));
        if (users.isEmpty()) {
            return null;
        }

        int revoked = jdbcTemplate.update("""
                UPDATE refresh_tokens
                SET revoked_at = ?
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                  AND expires_at > ?
                """, java.sql.Timestamp.valueOf(now), tokenHash, java.sql.Timestamp.valueOf(now));
        if (revoked != 1) {
            return null;
        }
        RefreshUser row = users.get(0);
        List<String> roles = loadRoles(row.id());
        if (roles.isEmpty()) {
            return null;
        }
        return createSession(new AuthenticatedUser(
                row.id(), row.username(), row.displayName(), roles, row.profileCode()));
    }

    public void revokeRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE refresh_tokens
                SET revoked_at = ?
                WHERE token_hash = ? AND revoked_at IS NULL
                """, java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)),
                hashRefreshToken(refreshToken));
    }

    public AuthenticatedUser findByAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload();
            List<String> roles = readRoles(claims.get("roles"));
            if (roles.isEmpty()) {
                return null;
            }
            return new AuthenticatedUser(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    claims.get("fullName", String.class),
                    roles,
                    claims.get("studentCode", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String issueAccessToken(AuthenticatedUser user) {
        var now = java.time.Instant.now();
        return Jwts.builder()
                .subject(user.id())
                .issuer("academic-portal")
                .claim("username", user.username())
                .claim("fullName", user.fullName())
                .claim("roles", normalizeRoles(user.roles()))
                .claim("studentCode", user.studentCode())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private void storeRefreshToken(String userId, String refreshToken) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, hashRefreshToken(refreshToken),
                java.sql.Timestamp.valueOf(now.plusDays(REFRESH_TOKEN_TTL_DAYS)),
                java.sql.Timestamp.valueOf(now));
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashRefreshToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private List<String> loadRoles(String userId) {
        return jdbcTemplate.query("""
                SELECT role.code
                FROM user_roles userRole
                JOIN roles role ON role.id = userRole.role_id
                WHERE userRole.user_id = ?
                  AND userRole.revoked_at IS NULL
                  AND role.is_active = 1
                ORDER BY role.code
                """, (rs, rowNum) -> normalizeRole(rs.getString("code")), userId);
    }

    private List<String> readRoles(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (Object role : values) {
            if (role instanceof String text && !text.isBlank()) {
                roles.add(normalizeRole(text));
            }
        }
        return normalizeRoles(roles);
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(this::normalizeRole)
                .distinct()
                .sorted()
                .toList();
    }

    private String normalizeRole(String role) {
        return role.toUpperCase(Locale.ROOT);
    }

    public record AuthenticatedUser(
            String id, String username, String fullName, List<String> roles, String studentCode) {
        public AuthenticatedUser {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record SessionTokens(String accessToken, String refreshToken) {
    }

    private record RefreshUser(String id, String username, String displayName, String profileCode) {
    }
}
