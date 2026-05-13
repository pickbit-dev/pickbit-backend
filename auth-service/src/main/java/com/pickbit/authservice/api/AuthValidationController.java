package com.pickbit.authservice.api;

import com.pickbit.authservice.api.dto.request.ValidateTokenRequest;
import com.pickbit.authservice.api.dto.response.ValidateTokenResponse;
import com.pickbit.authservice.application.query.AuthQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway가 access token을 검증하고 사용자 컨텍스트를 조회할 때 사용하는 인증 검증 API 컨트롤러입니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthValidationController {

    private final AuthQueryService authQueryService;

    /**
     * access token을 검증하고 Gateway가 내부 헤더로 전달할 사용자 정보를 반환합니다.
     *
     * @param request 검증할 access token
     * @return 토큰에 포함된 사용자 식별자, 권한, 닉네임, provider, 이메일 정보
     */
    @PostMapping("/validate")
    public ValidateTokenResponse validate(@Valid @RequestBody ValidateTokenRequest request) {
        return authQueryService.validate(request);
    }
}
