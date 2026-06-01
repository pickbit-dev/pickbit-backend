package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.application.command.AuthCommandService;
import com.pickbit.authservice.infrastructure.redis.OAuthExchangeCodeRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthSignupCodeRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration EXCHANGE_CODE_TTL = Duration.ofMinutes(3);
    private static final Duration SIGNUP_CODE_TTL = Duration.ofMinutes(10);

    private final AuthCommandService authCommandService;
    private final OAuthExchangeCodeRepository exchangeCodeRepository;
    private final OAuthSignupCodeRepository signupCodeRepository;
    private final OAuthUserInfoExtractor userInfoExtractor;

    @Value("${frontend.oauth-callback-url}")
    private String frontendCallbackUrl;

    @Value("${frontend.oauth-signup-url}")
    private String frontendSignupUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        log.info("OAuth success handler started | provider={}", registrationId);

        OAuth2User oauthUser = oauthToken.getPrincipal();
        OAuthUserInfo userInfo = userInfoExtractor.extract(registrationId, oauthUser);
        OAuthLoginResult result = authCommandService.oauthLogin(userInfo);
        log.info("OAuth login result | provider={} | requiresSignup={}", registrationId, result.requiresSignup());

        String code = UUID.randomUUID().toString();
        if (result.requiresSignup()) {
            signupCodeRepository.save(code, result.signupContext(), SIGNUP_CODE_TTL);
            String redirectUrl = frontendSignupUrl + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
            log.info("OAuth redirecting to signup | provider={} | redirectUrl={}", registrationId, redirectUrl);
            response.sendRedirect(redirectUrl);
            return;
        }

        exchangeCodeRepository.save(code, result.tokenResponse(), EXCHANGE_CODE_TTL);
        String redirectUrl = frontendCallbackUrl + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
        log.info("OAuth redirecting to callback | provider={} | redirectUrl={}", registrationId, redirectUrl);
        response.sendRedirect(redirectUrl);
    }
}
