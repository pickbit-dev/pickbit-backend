package com.pickbit.userservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import com.pickbit.userservice.domain.enums.PenaltyReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPenalty extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PenaltyReason reason;

    @Column(nullable = false)
    private Integer scoreDelta;

    @Column(nullable = false)
    private Integer scoreAfter;

    @Column(nullable = false, unique = true, length = 120)
    private String sourceEventId;

    private Long paymentId;

    private Long auctionId;

    private Long productId;
}
