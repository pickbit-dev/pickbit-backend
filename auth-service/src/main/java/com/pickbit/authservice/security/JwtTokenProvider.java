package com.pickbit.authservice.security;

import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.domain.enums.Role;
import com.pickbit.authservice.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    @Getter
    private final long accessTokenValidityMs;

    @Getter
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-ms:1800000}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity-ms:1209600000}") long refreshTokenValidityMs
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret은 HS256 사용을 위해 32바이트 이상이어야 합니다.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String createAccessToken(AuthAccount account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(accessTokenValidityMs);
        return Jwts.builder()
                .subject(String.valueOf(account.getId()))
                .claim("email", account.getEmail())
                .claim("nickname", resolveNickname(account))
                .claim("role", account.getRole().name())
                .claim("provider", account.getOauthProvider().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(AuthAccount account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(refreshTokenValidityMs);
        return Jwts.builder()
                .subject(String.valueOf(account.getId()))
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public AuthPrincipal parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        String role = claims.get("role", String.class);
        String provider = claims.get("provider", String.class);
        String email = claims.get("email", String.class);
        String nickname = claims.get("nickname", String.class);
        if (role == null || provider == null || email == null || nickname == null) {
            throw new InvalidTokenException("access token claim이 올바르지 않습니다.");
        }
        return new AuthPrincipal(
                Long.valueOf(claims.getSubject()),
                email,
                nickname,
                Role.valueOf(role),
                OAuthProvider.valueOf(provider)
        );
    }

    public Long parseRefreshTokenSubject(String token) {
        Claims claims = parseClaims(token);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new InvalidTokenException("refresh token이 아닙니다.");
        }
        return Long.valueOf(claims.getSubject());
    }

    public Instant getExpiresAt(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다.");
        }
    }

    private String resolveNickname(AuthAccount account) {
        if (StringUtils.hasText(account.getNickname())) {
            return account.getNickname();
        }
        return account.getEmail().split("@")[0];
    }
}
