package com.example.quanlydaotao.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

    public String hash(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            spec.clearPassword();
            return "pbkdf2$120000$" + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
            throw new IllegalStateException("Cannot hash password", e);
        }
    }

    public boolean matches(String password, String storedHash) {
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
            if (parts.length != 4 || !"120000".equals(parts[1])) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            spec.clearPassword();
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | NoSuchAlgorithmException
                | java.security.spec.InvalidKeySpecException e) {
            return false;
        }
    }
}
