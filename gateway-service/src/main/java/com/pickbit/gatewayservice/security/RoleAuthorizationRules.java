package com.pickbit.gatewayservice.security;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 경로 단위 역할 인가 규칙입니다.
 *
 * <p>서비스 쪽 소유자 검증({@code validateOwner}, {@code ensureBuyer} 등)을 대체하는 것이 아니라
 * 그 앞단에 한 겹 더 두는 것입니다. 게이트웨이에서 막으면 권한 없는 요청이 다운스트림 서비스와
 * DB 커넥션을 아예 소모하지 않습니다.
 */
@Component
public class RoleAuthorizationRules {

    private static final String ADMIN_ROLE = "ADMIN";

    private record Rule(HttpMethod method, String pathPrefix, String requiredRole) {
        boolean matches(HttpMethod requestMethod, String path) {
            return method.equals(requestMethod) && path.startsWith(pathPrefix);
        }
    }

    /**
     * 카테고리는 전역 데이터라 관리자만 변경할 수 있어야 한다.
     * (product-service의 CategoryController에도 같은 검사가 있다 — 다중 방어)
     */
    private static final List<Rule> RULES = List.of(
            new Rule(HttpMethod.POST, "/api/categories", ADMIN_ROLE),
            new Rule(HttpMethod.PATCH, "/api/categories", ADMIN_ROLE),
            new Rule(HttpMethod.DELETE, "/api/categories", ADMIN_ROLE)
    );

    /**
     * 호출자가 해당 경로를 호출할 권한이 있는지 확인합니다.
     *
     * @return 규칙에 걸리지 않거나 역할이 충족되면 {@code true}
     */
    public boolean isAllowed(HttpMethod method, String path, String role) {
        return RULES.stream()
                .filter(rule -> rule.matches(method, path))
                .allMatch(rule -> rule.requiredRole().equals(role));
    }
}
