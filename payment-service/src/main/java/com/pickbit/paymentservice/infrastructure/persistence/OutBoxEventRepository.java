package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.domain.OutBoxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutBoxEventRepository extends JpaRepository<OutBoxEvent, Long> {
}
