package com.pickbit.auctionservice.domain;

import com.pickbit.auctionservice.domain.enums.BidStatus;
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
public class Bid extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, comment = "경매 ID")
    private Auction auction;

    @Column(comment = "입찰자 사용자 ID")
    private Long bidderUserId;

    @Column(comment = "입찰자 닉네임", nullable = false, length = 50)
    private String bidderNickname;

    @Column(comment = "입찰 금액", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(comment = "입찰 시각", nullable = false)
    private LocalDateTime bidTime;

    @Enumerated(EnumType.STRING)
    @Column(comment = "입찰 상태", nullable = false, length = 10)
    private BidStatus bidStatus;

    public void markOutbid() {
        this.bidStatus = BidStatus.OUTBID;
    }

    public void markWinning() {
        this.bidStatus = BidStatus.WINNING;
    }
}
