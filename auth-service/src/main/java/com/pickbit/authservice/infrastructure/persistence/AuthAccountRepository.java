package com.pickbit.authservice.infrastructure.persistence;

import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    boolean existsByEmailAndOauthProvider(String email, OAuthProvider oauthProvider);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<AuthAccount> findByEmailAndOauthProvider(String email, OAuthProvider oauthProvider);

    Optional<AuthAccount> findByOauthProviderAndOauthProviderId(OAuthProvider oauthProvider, String oauthProviderId);
}
