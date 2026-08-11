package com.pickbit.gatewayservice.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 테스트용 API key 설정입니다.
 *
 * <p>이 키는 JWT 없이 임의의 사용자로 요청을 보낼 수 있게 해주므로 사실상 인증 우회입니다.
 * 부하 테스트에서 사용자 수백 명분의 토큰을 매번 발급받는 부담을 없애기 위한 장치이며,
 * 다음 규칙을 지켜야 합니다.
 *
 * <ul>
 *   <li>기본값은 비활성이고, {@code key}가 비어 있으면 활성으로 설정해도 동작하지 않는다</li>
 *   <li>키는 시크릿 파일에서 주입한다. 절대 커밋하지 않는다</li>
 *   <li>충분히 긴 무작위 값을 쓴다 (예: {@code openssl rand -hex 32})</li>
 *   <li>키가 유출되면 누구나 임의 사용자로 API를 호출할 수 있다. 유출이 의심되면 즉시 교체한다</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gateway.api-key")
public class ApiKeyProperties {

    /** 활성 여부. 기본 비활성. */
    private boolean enabled = false;

    /** 공유 비밀 키. 비어 있으면 enabled와 무관하게 비활성으로 취급한다. */
    private String key = "";

    /** ADMIN 역할로 요청하는 것을 허용할지 여부. 기본 불허. */
    private boolean allowAdminRole = false;

    /**
     * 실제로 사용 가능한 상태인지 확인합니다. 키가 비어 있으면 설정 실수로 인증이
     * 통째로 열리는 일이 없도록 항상 비활성으로 판단합니다.
     */
    public boolean isUsable() {
        return enabled && key != null && !key.isBlank();
    }
}
