package com.pickbit.userservice.infrastructure.persistence;

import com.pickbit.userservice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAccountId(Long accountId);

    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndAccountIdNot(String nickname, Long accountId);
}
