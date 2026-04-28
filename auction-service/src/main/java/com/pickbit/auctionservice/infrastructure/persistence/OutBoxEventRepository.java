package com.pickbit.auctionservice.infrastructure.persistence;

import com.pickbit.auctionservice.domain.OutBoxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutBoxEventRepository extends JpaRepository<OutBoxEvent, Long> {
}
