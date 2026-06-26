package com.pickbit.authservice.infrastructure.persistence;

import com.pickbit.authservice.domain.AuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<AuthAccount> findByEmail(String email);
}
