package com.pickbit.authservice.domain;

import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "auth_provider_link",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auth_provider_link_provider_id", columnNames = {"provider", "providerId"}),
                @UniqueConstraint(name = "uk_auth_provider_link_account_provider", columnNames = {"account_id", "provider"})
        },
        indexes = @Index(name = "idx_auth_provider_link_account_id", columnList = "account_id")
)
public class AuthProviderLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_auth_provider_link_account"))
    private AuthAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(nullable = false, length = 255)
    private String providerId;

    public static AuthProviderLink create(AuthAccount account, OAuthProvider provider, String providerId) {
        return AuthProviderLink.builder()
                .account(account)
                .provider(provider)
                .providerId(providerId)
                .build();
    }
}
