package com.pickbit.userservice.infrastructure.persistence;

import com.pickbit.userservice.domain.UserPenalty;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPenaltyRepository extends JpaRepository<UserPenalty, Long> {
}
