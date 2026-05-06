package com.pickbit.authservice.application.query;

import com.pickbit.authservice.api.dto.request.ValidateTokenRequest;
import com.pickbit.authservice.api.dto.response.ValidateTokenResponse;
import com.pickbit.authservice.security.AuthPrincipal;
import com.pickbit.authservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthQueryService {

    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public ValidateTokenResponse validate(ValidateTokenRequest request) {
        AuthPrincipal principal = jwtTokenProvider.parseAccessToken(request.token());
        return new ValidateTokenResponse(
                principal.accountId(),
                principal.email(),
                principal.role(),
                principal.provider(),
                jwtTokenProvider.getExpiresAt(request.token())
        );
    }
}
