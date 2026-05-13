package com.pickbit.userservice.infrastructure.persistence;

import com.pickbit.userservice.domain.OutBoxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutBoxEventRepository extends JpaRepository<OutBoxEvent, Long> {
}
