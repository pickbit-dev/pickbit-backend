package com.pickbit.auctionservice.domain;

import com.pickbit.auctionservice.domain.enums.AuctionEventType;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, comment = "경매 ID")
    private Auction auction;

    @Enumerated(EnumType.STRING)
    @Column(comment = "이벤트 종류", nullable = false, length = 30)
    private AuctionEventType eventType;

    @Column(comment = "입찰 ID")
    private Long bidId;

    @Enumerated(EnumType.STRING)
    @Column(comment = "경매 상태", nullable = false, length = 20)
    private AuctionStatus auctionStatus;

    @Column(comment = "현재 최고 입찰가", precision = 19, scale = 2)
    private BigDecimal currentPrice;

    @Column(comment = "입찰자 닉네임", length = 50)
    private String bidderNickname;

    @Column(comment = "입찰 시각")
    private LocalDateTime bidTime;

    @Column(comment = "낙찰자 닉네임", length = 50)
    private String winnerNickname;

    @Column(comment = "최종 낙찰가", precision = 19, scale = 2)
    private BigDecimal finalPrice;
}
