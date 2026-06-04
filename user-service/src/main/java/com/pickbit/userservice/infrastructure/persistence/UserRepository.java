package com.pickbit.userservice.infrastructure.persistence;

import com.pickbit.userservice.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.accountId = :accountId")
    Optional<User> findByAccountIdForUpdate(@Param("accountId") Long accountId);

    Optional<User> findByNickname(String nickname);

    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndAccountIdNot(String nickname, Long accountId);
}
