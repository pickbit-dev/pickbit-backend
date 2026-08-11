package com.pickbit.gatewayservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * {@code X-Api-Key} 헤더로 인증을 대신 통과시키는 테스트용 경로입니다.
 *
 * <p>사용 예 — 토큰 발급 없이 42번 사용자로 요청:
 * <pre>
 * curl -H "X-Api-Key: $PICKBIT_API_KEY" \
 *      -H "X-Api-User-Id: 42" \
 *      -H "X-Api-Nickname: tester42" \
 *      https://api.pickbit.co.kr/api/products/me/selling
 * </pre>
 *
 * <p>{@link ApiKeyProperties}에 적힌 대로 기본 비활성이며, 키가 비어 있으면 동작하지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticator {

    public static final String API_KEY_HEADER = "X-Api-Key";
    public static final String API_USER_ID_HEADER = "X-Api-User-Id";
    public static final String API_NICKNAME_HEADER = "X-Api-Nickname";
    public static final String API_ROLE_HEADER = "X-Api-Role";
    public static final String API_EMAIL_HEADER = "X-Api-Email";

    private static final String DEFAULT_ROLE = "USER";
    private static final String DEFAULT_PROVIDER = "LOCAL";
    private static final String ADMIN_ROLE = "ADMIN";

    private final ApiKeyProperties properties;

    public boolean isPresent(HttpHeaders headers) {
        return StringUtils.hasText(headers.getFirst(API_KEY_HEADER));
    }

    /**
     * API key를 검증하고 헤더에 지정된 신원을 만들어 돌려줍니다.
     *
     * @throws InvalidTokenException 키가 비활성이거나 일치하지 않거나 사용자 ID가 없는 경우
     */
    public AuthenticatedUser authenticate(HttpHeaders headers) {
        if (!properties.isUsable()) {
            throw new InvalidTokenException("API key 인증이 활성화되어 있지 않습니다.");
        }

        String presented = headers.getFirst(API_KEY_HEADER);
        if (!matches(presented)) {
            log.warn("API key 불일치로 거부했습니다.");
            throw new InvalidTokenException("API key가 올바르지 않습니다.");
        }

        String userId = headers.getFirst(API_USER_ID_HEADER);
        if (!StringUtils.hasText(userId)) {
            throw new InvalidTokenException(API_USER_ID_HEADER + " 헤더로 사용자 ID를 지정해야 합니다.");
        }

        long accountId;
        try {
            accountId = Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            throw new InvalidTokenException(API_USER_ID_HEADER + " 헤더는 숫자여야 합니다.");
        }

        String role = resolveRole(headers.getFirst(API_ROLE_HEADER));
        String nickname = StringUtils.hasText(headers.getFirst(API_NICKNAME_HEADER))
                ? headers.getFirst(API_NICKNAME_HEADER)
                : "apikey-user-" + accountId;
        String email = StringUtils.hasText(headers.getFirst(API_EMAIL_HEADER))
                ? headers.getFirst(API_EMAIL_HEADER)
                : "apikey-user-" + accountId + "@pickbit.local";

        // 우회 경로는 항상 흔적을 남긴다.
        log.info("API key 인증 통과 | accountId={} | role={}", accountId, role);

        return new AuthenticatedUser(accountId, email, nickname, role, DEFAULT_PROVIDER);
    }

    private String resolveRole(String requested) {
        if (!StringUtils.hasText(requested)) {
            return DEFAULT_ROLE;
        }
        String role = requested.trim().toUpperCase();
        if (ADMIN_ROLE.equals(role) && !properties.isAllowAdminRole()) {
            throw new InvalidTokenException("이 API key로는 ADMIN 역할을 사용할 수 없습니다.");
        }
        return role;
    }

    /** 타이밍 공격을 피하기 위해 상수 시간으로 비교한다. */
    private boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.getKey().getBytes(StandardCharsets.UTF_8));
    }
}
