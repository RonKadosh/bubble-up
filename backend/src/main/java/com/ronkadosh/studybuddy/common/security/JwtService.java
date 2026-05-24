package com.ronkadosh.studybuddy.common.security;

import com.ronkadosh.studybuddy.common.config.SecurityProperties;
import com.ronkadosh.studybuddy.common.context.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(SecurityProperties props) {
        this.secretKey = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.jwtAccessExpirationMs();
    }

    public String generateToken(UUID userId, String email, UserRole role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(
                        SecurityConstants.EMAIL_CLAIM, email,
                        SecurityConstants.ROLE_CLAIM, role.name()
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return parseClaims(token).get(SecurityConstants.EMAIL_CLAIM, String.class);
    }

    public UserRole extractRole(String token) {
        return UserRole.valueOf(parseClaims(token).get(SecurityConstants.ROLE_CLAIM, String.class));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
