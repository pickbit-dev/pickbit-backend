package com.pickbit.authservice.api;

import com.pickbit.authservice.api.dto.request.LoginRequest;
import com.pickbit.authservice.api.dto.request.LogoutRequest;
import com.pickbit.authservice.api.dto.request.RefreshRequest;
import com.pickbit.authservice.api.dto.request.SignupRequest;
import com.pickbit.authservice.api.dto.response.AuthAccountResponse;
import com.pickbit.authservice.api.dto.response.TokenResponse;
import com.pickbit.authservice.application.AuthCookieService;
import com.pickbit.authservice.application.command.AuthCommandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 계정의 가입, 로그인, 토큰 재발급, 로그아웃 API를 제공하는 컨트롤러입니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthCommandService authCommandService;
    private final AuthCookieService authCookieService;

    /**
     * 로컬 계정을 생성합니다.
     *
     * @param request 가입할 이메일, 비밀번호, 닉네임 정보
     * @return 생성된 인증 계정 정보
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthAccountResponse signup(@Valid @RequestBody SignupRequest request) {
        return authCommandService.signup(request);
    }

    /**
     * 이메일과 비밀번호로 로그인하고 access token과 refresh token을 발급합니다.
     *
     * @param request 로그인할 이메일과 비밀번호
     * @return 발급된 토큰 정보
     */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        TokenResponse tokenResponse = authCommandService.login(request);
        authCookieService.addTokenCookies(response, tokenResponse);
        return tokenResponse;
    }

    /**
     * 유효한 refresh token으로 access token과 refresh token을 재발급합니다.
     *
     * @param request 재발급에 사용할 refresh token
     * @return 재발급된 토큰 정보
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        TokenResponse tokenResponse = authCommandService.refresh(resolveRefreshToken(request, servletRequest));
        authCookieService.addTokenCookies(response, tokenResponse);
        return tokenResponse;
    }

    /**
     * refresh token을 폐기하여 로그아웃 처리합니다.
     *
     * @param request 폐기할 refresh token
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        authCommandService.logout(resolveRefreshToken(request, servletRequest));
        authCookieService.clearTokenCookies(response);
    }

    private String resolveRefreshToken(RefreshRequest request, HttpServletRequest servletRequest) {
        if (request != null && request.refreshToken() != null) {
            return request.refreshToken();
        }
        return authCookieService.getRefreshToken(servletRequest);
    }

    private String resolveRefreshToken(LogoutRequest request, HttpServletRequest servletRequest) {
        if (request != null && request.refreshToken() != null) {
            return request.refreshToken();
        }
        return authCookieService.getRefreshToken(servletRequest);
    }
}
