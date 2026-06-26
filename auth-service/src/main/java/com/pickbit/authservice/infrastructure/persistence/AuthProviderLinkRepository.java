package com.pickbit.authservice.infrastructure.persistence;

import com.pickbit.authservice.domain.AuthProviderLink;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthProviderLinkRepository extends JpaRepository<AuthProviderLink, Long> {

    boolean existsByAccountIdAndProvider(Long accountId, OAuthProvider provider);

    boolean existsByProviderAndProviderId(OAuthProvider provider, String providerId);

    Optional<AuthProviderLink> findByProviderAndProviderId(OAuthProvider provider, String providerId);
}
