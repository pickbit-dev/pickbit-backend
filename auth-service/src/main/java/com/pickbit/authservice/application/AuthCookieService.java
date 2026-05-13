package com.pickbit.authservice.application;

import com.pickbit.authservice.api.dto.response.TokenResponse;
import com.pickbit.authservice.config.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthCookieService {

    private final AuthCookieProperties properties;

    public void addTokenCookies(HttpServletResponse response, TokenResponse tokenResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie(
                properties.getAccessTokenName(),
                tokenResponse.accessToken(),
                Duration.ofMillis(tokenResponse.accessTokenExpiresIn())
        ).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie(
                properties.getRefreshTokenName(),
                tokenResponse.refreshToken(),
                Duration.ofMillis(tokenResponse.refreshTokenExpiresIn())
        ).toString());
    }

    public void clearTokenCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie(properties.getAccessTokenName(), "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie(properties.getRefreshTokenName(), "", Duration.ZERO).toString());
    }

    public String getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, properties.getRefreshTokenName());
    }

    private ResponseCookie createCookie(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .path(properties.getPath())
                .maxAge(maxAge)
                .httpOnly(properties.isHttpOnly())
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite());
        if (StringUtils.hasText(properties.getDomain())) {
            builder.domain(properties.getDomain());
        }
        return builder.build();
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
