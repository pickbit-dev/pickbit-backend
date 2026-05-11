package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.api.dto.response.TokenResponse;
import com.pickbit.authservice.application.command.AuthCommandService;
import com.pickbit.authservice.infrastructure.redis.OAuthExchangeCodeRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration EXCHANGE_CODE_TTL = Duration.ofMinutes(3);

    private final AuthCommandService authCommandService;
    private final OAuthExchangeCodeRepository exchangeCodeRepository;
    private final OAuthUserInfoExtractor userInfoExtractor;

    @Value("${frontend.oauth-callback-url:http://localhost:3000/oauth/callback}")
    private String frontendCallbackUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();
        OAuthUserInfo userInfo = userInfoExtractor.extract(oauthToken.getAuthorizedClientRegistrationId(), oauthUser);
        TokenResponse tokenResponse = authCommandService.oauthLogin(userInfo);

        String code = UUID.randomUUID().toString();
        exchangeCodeRepository.save(code, tokenResponse, EXCHANGE_CODE_TTL);
        response.sendRedirect(frontendCallbackUrl + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }
}
