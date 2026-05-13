package com.pickbit.authservice.api;

import com.pickbit.authservice.api.dto.request.OAuthExchangeRequest;
import com.pickbit.authservice.api.dto.request.OAuthSignupCompleteRequest;
import com.pickbit.authservice.api.dto.response.OAuthSignupContextResponse;
import com.pickbit.authservice.api.dto.response.TokenResponse;
import com.pickbit.authservice.application.AuthCookieService;
import com.pickbit.authservice.application.command.AuthCommandService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth 로그인 완료 후 임시 exchange code를 서비스 토큰으로 교환하는 API 컨트롤러입니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/oauth")
public class OAuthController {

    private final AuthCommandService authCommandService;
    private final AuthCookieService authCookieService;

    /**
     * OAuth 신규 가입용 code로 provider가 제공한 기본 사용자 정보를 조회합니다.
     *
     * @param code OAuth 신규 가입용 임시 code
     * @return 추가 정보 입력 화면에 표시할 provider, 이메일, 닉네임 기본값
     */
    @GetMapping("/signup-context")
    public OAuthSignupContextResponse signupContext(@RequestParam String code) {
        return authCommandService.getOAuthSignupContext(code);
    }

    /**
     * OAuth 신규 사용자의 추가 정보를 받아 가입을 완료하고 토큰을 발급합니다.
     *
     * @param request OAuth 신규 가입용 code, 이메일, 닉네임
     * @return 발급된 토큰 정보
     */
    @PostMapping("/signup")
    public TokenResponse signup(@Valid @RequestBody OAuthSignupCompleteRequest request, HttpServletResponse response) {
        TokenResponse tokenResponse = authCommandService.completeOAuthSignup(request);
        authCookieService.addTokenCookies(response, tokenResponse);
        return tokenResponse;
    }

    /**
     * OAuth redirect 과정에서 발급된 exchange code를 access token과 refresh token으로 교환합니다.
     *
     * @param request OAuth exchange code
     * @return 발급된 토큰 정보
     */
    @PostMapping("/exchange")
    public TokenResponse exchange(@Valid @RequestBody OAuthExchangeRequest request, HttpServletResponse response) {
        TokenResponse tokenResponse = authCommandService.exchangeOAuthCode(request);
        authCookieService.addTokenCookies(response, tokenResponse);
        return tokenResponse;
    }
}
