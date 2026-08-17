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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
// 유니크여야 한다. 스트림 재배달로 같은 입찰이 두 번 영속화되면 순번이 중복되는데,
// 순번은 클라이언트의 누락 이벤트 복구(afterEventId) 기준이라 중복되면 복구가 어긋난다.
// 애플리케이션 단의 순번 스킵(BidBatchPersister)이 1차 방어, 이 제약이 최종 방어다.
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_auction_event_auction_sequence", columnNames = {"auction_id", "sequence"}))
public class AuctionEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, comment = "경매 ID")
    private Auction auction;

    /**
     * 경매 안에서의 이벤트 순번입니다.
     *
     * <p>입찰이 비동기로 기록되면서 DB의 auto-increment id 순서가 실제 입찰 순서를 보장하지 못하게
     * 됐습니다. 순번은 Redis 가 입찰을 수락하는 순간 원자적으로 발급하며,
     * 클라이언트의 누락 이벤트 복구({@code ?afterEventId=})도 이 값을 기준으로 합니다.
     */
    @Column(comment = "경매 내 이벤트 순번", nullable = false)
    private Long sequence;

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
