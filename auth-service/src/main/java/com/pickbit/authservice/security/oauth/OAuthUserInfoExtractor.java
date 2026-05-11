package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.domain.enums.OAuthProvider;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class OAuthUserInfoExtractor {

    public OAuthUserInfo extract(String registrationId, OAuth2User user) {
        OAuthProvider provider = OAuthProvider.valueOf(registrationId.toUpperCase());
        Map<String, Object> attributes = user.getAttributes();
        return switch (provider) {
            case GOOGLE -> google(attributes);
            case KAKAO -> kakao(attributes);
            case NAVER -> naver(attributes);
            case LOCAL -> throw new IllegalArgumentException("LOCAL은 OAuth provider가 아닙니다.");
        };
    }

    private OAuthUserInfo google(Map<String, Object> attributes) {
        String providerId = stringValue(attributes.get("sub"));
        String email = stringValue(attributes.get("email"));
        String nickname = stringValue(attributes.get("name"));
        return validate(new OAuthUserInfo(OAuthProvider.GOOGLE, providerId, email, nickname));
    }

    @SuppressWarnings("unchecked")
    private OAuthUserInfo kakao(Map<String, Object> attributes) {
        String providerId = stringValue(attributes.get("id"));
        Map<String, Object> account = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        Map<String, Object> properties = (Map<String, Object>) attributes.getOrDefault("properties", Map.of());
        String email = stringValue(account.get("email"));
        String nickname = stringValue(properties.get("nickname"));
        if (!StringUtils.hasText(email)) {
            email = providerId + "@kakao.oauth.pickbit.local";
        }
        return validate(new OAuthUserInfo(OAuthProvider.KAKAO, providerId, email, nickname));
    }

    @SuppressWarnings("unchecked")
    private OAuthUserInfo naver(Map<String, Object> attributes) {
        Map<String, Object> response = (Map<String, Object>) attributes.getOrDefault("response", Map.of());
        String providerId = stringValue(response.get("id"));
        String email = stringValue(response.get("email"));
        String nickname = stringValue(response.get("nickname"));
        return validate(new OAuthUserInfo(OAuthProvider.NAVER, providerId, email, nickname));
    }

    private OAuthUserInfo validate(OAuthUserInfo userInfo) {
        if (!StringUtils.hasText(userInfo.providerId()) || !StringUtils.hasText(userInfo.email())) {
            throw new IllegalArgumentException("OAuth 사용자 식별 정보가 올바르지 않습니다. provider=" + userInfo.provider());
        }
        return userInfo;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
