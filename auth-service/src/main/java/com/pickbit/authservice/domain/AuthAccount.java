package com.pickbit.authservice.domain;

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
        uniqueConstraints = @UniqueConstraint(name = "uk_auth_account_email", columnNames = "email"),
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
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column
    private LocalDateTime lastLoginAt;

    public static AuthAccount local(String email, String encodedPassword, String nickname) {
        return AuthAccount.builder()
                .email(email)
                .nickname(normalizeNickname(nickname, email))
                .password(encodedPassword)
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    public static AuthAccount oauth(String email, String nickname) {
        return AuthAccount.builder()
                .email(email)
                .nickname(normalizeNickname(nickname, email))
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void changeNickname(String nickname) {
        this.nickname = normalizeNickname(nickname, email);
    }

    public static String normalizeNickname(String nickname, String email) {
        if (StringUtils.hasText(nickname)) {
            String normalized = nickname.replaceAll("\\s+", "");
            if (normalized.length() >= 2) {
                return normalized.length() <= 20 ? normalized : normalized.substring(0, 20);
            }
        }
        String fallback = email.split("@")[0];
        return fallback.length() <= 20 ? fallback : fallback.substring(0, 20);
    }
}
