package com.pickbit.gatewayservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * access token을 게이트웨이에서 직접 검증합니다.
 *
 * <p>이전에는 요청마다 auth-service의 {@code /api/auth/validate}를 HTTP로 호출했습니다.
 * 그런데 그 엔드포인트는 리포지토리를 전혀 사용하지 않는 순수 JWT 파싱이었기 때문에,
 * 같은 시크릿으로 게이트웨이에서 검증해도 기능상 달라지는 것이 없습니다.
 * 대신 인증된 요청마다 발생하던 네트워크 왕복과 auth-service의 DB 커넥션 점유가 사라집니다.
 *
 * <p>토큰 폐기(로그아웃)는 이전에도 검증 경로에서 확인하지 않았으므로 동작이 동일합니다.
 * 즉시 폐기가 필요해지면 여기에 Redis 기반 거부 목록을 추가하면 됩니다.
 *
 * <p>서명 알고리즘과 클레임 구조는 auth-service의 {@code JwtTokenProvider}와 반드시 일치해야 합니다.
 */
@Component
public class GatewayJwtDecoder {

    private final SecretKey secretKey;

    public GatewayJwtDecoder(@Value("${jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret은 HS256 사용을 위해 32바이트 이상이어야 합니다.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthenticatedUser decode(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다.");
        }

        String role = claims.get("role", String.class);
        String provider = claims.get("provider", String.class);
        String email = claims.get("email", String.class);
        String nickname = claims.get("nickname", String.class);
        if (role == null || provider == null || email == null || nickname == null) {
            throw new InvalidTokenException("access token claim이 올바르지 않습니다.");
        }

        // refresh token 으로 API를 호출하는 것을 막는다.
        if (claims.get("type", String.class) != null) {
            throw new InvalidTokenException("access token이 아닙니다.");
        }

        return new AuthenticatedUser(
                Long.valueOf(claims.getSubject()),
                email,
                nickname,
                role,
                provider
        );
    }
}
