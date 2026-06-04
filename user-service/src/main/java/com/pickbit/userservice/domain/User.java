package com.pickbit.userservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long accountId;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    @Builder.Default
    private Integer trustScore = 100;

    public static User create(Long accountId, String email, String nickname, String provider, String role) {
        return User.builder()
                .accountId(accountId)
                .email(email)
                .nickname(nickname)
                .provider(provider)
                .role(role)
                .build();
    }

    public void changeProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public int applyTrustScoreDelta(int delta) {
        this.trustScore = Math.clamp(this.trustScore + delta, 0, 100);
        return this.trustScore;
    }
}
