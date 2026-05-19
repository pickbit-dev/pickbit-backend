package com.pickbit.auctionservice.domain;

import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
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
public class Auction extends BaseEntity {

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(comment = "상품 ID (product-service 참조)", nullable = false)
    private Long productId;

    @Column(comment = "상품명 스냅샷", nullable = false, length = 150)
    private String productName;

    @Column(comment = "썸네일 URL 스냅샷", length = 1000)
    private String productThumbnailUrl;

    @Column(comment = "판매자 사용자 ID")
    private Long sellerUserId;

    @Column(comment = "판매자 닉네임 스냅샷", nullable = false, length = 50)
    private String sellerNickname;

    @Column(comment = "시작가", nullable = false, precision = 19, scale = 2)
    private BigDecimal startingPrice;

    @Column(comment = "현재 최고 입찰가", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPrice;

    @Column(comment = "즉시 구매가 (null이면 미사용)", precision = 19, scale = 2)
    private BigDecimal buyNowPrice;

    @Column(comment = "최소 입찰 단위", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumBidIncrement;

    @Enumerated(EnumType.STRING)
    @Column(comment = "경매 상태", nullable = false, length = 20)
    private AuctionStatus auctionStatus;

    @Column(comment = "경매 시작 시각", nullable = false)
    private LocalDateTime startTime;

    @Column(comment = "경매 종료 시각", nullable = false)
    private LocalDateTime endTime;

    @Column(comment = "낙찰자 사용자 ID")
    private Long winnerUserId;

    @Column(comment = "낙찰자 닉네임", length = 50)
    private String winnerNickname;

    @Column(comment = "낙찰가", precision = 19, scale = 2)
    private BigDecimal finalPrice;

    public void activate() {
        this.auctionStatus = AuctionStatus.ACTIVE;
    }

    public void cancel() {
        this.auctionStatus = AuctionStatus.CANCELLED;
    }

    public void placeBid(BigDecimal amount) {
        this.currentPrice = amount;
    }

    public void complete(String winnerNickname, BigDecimal finalPrice) {
        this.auctionStatus = AuctionStatus.ENDED;
        this.winnerNickname = winnerNickname;
        this.finalPrice = finalPrice;
    }

    public void assignWinnerUserId(Long winnerUserId) {
        this.winnerUserId = winnerUserId;
    }

    public void endWithNoBids() {
        this.auctionStatus = AuctionStatus.ENDED;
    }
}
