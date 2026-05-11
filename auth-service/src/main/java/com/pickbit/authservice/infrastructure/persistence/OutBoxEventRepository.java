package com.pickbit.authservice.infrastructure.persistence;

import com.pickbit.authservice.domain.OutBoxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutBoxEventRepository extends JpaRepository<OutBoxEvent, Long> {
}
