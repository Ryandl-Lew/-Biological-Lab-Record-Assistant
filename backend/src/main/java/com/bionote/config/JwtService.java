package com.bionote.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(@Value("${bionote.jwt.secret}") String secret,
                      @Value("${bionote.jwt.ttl-minutes:120}") long ttlMinutes) {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("JWT secret must contain at least 32 characters");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    public String issue(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder().subject(userId.toString()).id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key).compact();
    }

    public UUID parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return UUID.fromString(claims.getSubject());
    }
}

