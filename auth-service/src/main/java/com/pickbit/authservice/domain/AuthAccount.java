package com.pickbit.authservice.domain;

import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.domain.enums.Role;
import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "auth_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auth_account_email_provider", columnNames = {"email", "oauthProvider"}),
                @UniqueConstraint(name = "uk_auth_account_provider_id", columnNames = {"oauthProvider", "oauthProviderId"})
        },
        indexes = @Index(name = "idx_auth_account_email", columnList = "email")
)
public class AuthAccount extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider oauthProvider;

    @Column(length = 255)
    private String oauthProviderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column
    private Boolean emailVerified;

    @Column
    private LocalDateTime lastLoginAt;

    public static AuthAccount local(String email, String encodedPassword, String nickname) {
        return AuthAccount.builder()
                .email(email)
                .nickname(normalizeNickname(nickname, email))
                .password(encodedPassword)
                .oauthProvider(OAuthProvider.LOCAL)
                .role(Role.USER)
                .enabled(true)
                .emailVerified(false)
                .build();
    }

    public static AuthAccount oauth(String email, OAuthProvider provider, String providerId, String nickname) {
        return AuthAccount.builder()
                .email(email)
                .nickname(normalizeNickname(nickname, email))
                .oauthProvider(provider)
                .oauthProviderId(providerId)
                .role(Role.USER)
                .enabled(true)
                .emailVerified(true)
                .build();
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    private static String normalizeNickname(String nickname, String email) {
        if (StringUtils.hasText(nickname)) {
            return nickname.length() <= 50 ? nickname : nickname.substring(0, 50);
        }
        String fallback = email.split("@")[0];
        return fallback.length() <= 50 ? fallback : fallback.substring(0, 50);
    }
}
