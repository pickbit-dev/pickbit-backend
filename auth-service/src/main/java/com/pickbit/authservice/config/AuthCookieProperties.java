package com.pickbit.authservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.cookie")
public class AuthCookieProperties {

    private String accessTokenName = "accessToken";
    private String refreshTokenName = "refreshToken";
    private String path = "/";
    private String domain;
    private boolean httpOnly = true;
    private boolean secure = false;
    private String sameSite = "Lax";
}
