package com.pickbit.paymentservice.api;

import com.pickbit.library.auth.AuthContextHolder;
import com.pickbit.library.dto.PageResponse;
import com.pickbit.library.dto.PageableRequest;
import com.pickbit.paymentservice.api.dto.response.SettlementResponse;
import com.pickbit.paymentservice.api.dto.response.SettlementSummaryResponse;
import com.pickbit.paymentservice.application.SettlementQueryService;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 정산 조회 API 컨트롤러.
 *
 * <p>구매확정 시 정산이 {@code PENDING} 으로 생성되고, 정산 배치가 수수료를 계산해
 * {@code COMPLETED} 로 전이시킵니다. 이 API 는 그 결과를 판매자가 확인하는 창구입니다.
 */
@Tag(name = "Settlement", description = "판매자 정산 API")
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementQueryService settlementQueryService;

    /**
     * 인증 사용자의 정산 내역을 페이징 조회합니다.
     *
     * @param status          정산 상태 필터 (생략하면 전체)
     * @param pageableRequest 페이징 조건
     * @return 내 정산 목록 (최신순)
     */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<SettlementResponse>> getMySettlements(
            @RequestParam(required = false) SettlementStatus status,
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(settlementQueryService.getMySettlements(
                AuthContextHolder.getUserId(), status, pageableRequest.toPageable(20)));
    }

    /**
     * 인증 사용자의 정산 요약(상태별 건수와 합계)을 조회합니다.
     *
     * @return 정산 요약
     */
    @GetMapping("/me/summary")
    public ResponseEntity<SettlementSummaryResponse> getMySummary() {
        return ResponseEntity.ok(settlementQueryService.getMySummary(AuthContextHolder.getUserId()));
    }

    /**
     * 정산 상세를 조회합니다. 본인 정산만 조회할 수 있습니다.
     *
     * @param settlementId 정산 ID
     * @return 정산 상세
     */
    @GetMapping("/{settlementId}")
    public ResponseEntity<SettlementResponse> getSettlement(@PathVariable Long settlementId) {
        return ResponseEntity.ok(settlementQueryService.getSettlement(
                AuthContextHolder.getUserId(), settlementId));
    }
}
