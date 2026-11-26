package com.example.quanlydaotao.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
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
        List<AuthenticatedUser> users = jdbcTemplate.query("""
                SELECT u.id, u.username, u.full_name, u.role_name, u.student_code
                FROM refresh_tokens rt
                JOIN users u ON u.id = rt.user_id
                WHERE rt.token_hash = ?
                  AND rt.revoked_at IS NULL
                  AND rt.expires_at > ?
                  AND u.is_active = 1
                """, (rs, rowNum) -> new AuthenticatedUser(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("full_name"),
                normalizeRole(rs.getString("role_name")),
                rs.getString("student_code")), tokenHash, java.sql.Timestamp.valueOf(now));
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
        return createSession(users.get(0));
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
            return new AuthenticatedUser(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    claims.get("fullName", String.class),
                    normalizeRole(claims.get("role", String.class)),
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
                .claim("role", normalizeRole(user.role()))
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

    private String normalizeRole(String role) {
        return role == null ? "STUDENT" : role.toUpperCase();
    }

    public record AuthenticatedUser(String id, String username, String fullName, String role, String studentCode) {
    }

    public record SessionTokens(String accessToken, String refreshToken) {
    }
}
