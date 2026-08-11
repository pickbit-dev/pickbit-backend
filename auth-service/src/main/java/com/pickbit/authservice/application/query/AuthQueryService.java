package com.pickbit.authservice.application.query;

import com.pickbit.authservice.api.dto.request.ValidateTokenRequest;
import com.pickbit.authservice.api.dto.response.ValidateTokenResponse;
import com.pickbit.authservice.security.AuthPrincipal;
import com.pickbit.authservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthQueryService {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 토큰을 검증합니다.
     *
     * <p>여기에는 트랜잭션을 걸지 않습니다. 리포지토리를 전혀 사용하지 않는 순수 JWT 파싱인데
     * {@code @Transactional}이 붙어 있으면 Hibernate가 autocommit 설정을 위해 JDBC 커넥션을
     * 미리 잡습니다. 예전에는 게이트웨이가 인증된 모든 요청마다 이 메서드를 호출했기 때문에
     * 시스템 전체의 인증 트래픽이 auth-service의 커넥션 풀을 소비하고 있었습니다.
     *
     * <p>게이트웨이는 이제 JWT를 자체 검증하므로 이 엔드포인트를 호출하지 않습니다.
     * 외부 호출자를 위해 남겨둡니다.
     */
    public ValidateTokenResponse validate(ValidateTokenRequest request) {
        AuthPrincipal principal = jwtTokenProvider.parseAccessToken(request.token());
        return new ValidateTokenResponse(
                principal.accountId(),
                principal.email(),
                principal.nickname(),
                principal.role(),
                principal.provider(),
                jwtTokenProvider.getExpiresAt(request.token())
        );
    }
}
