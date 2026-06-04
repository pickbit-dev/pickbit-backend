package com.pickbit.paymentservice.api;

import com.pickbit.library.auth.AuthContextHolder;
import com.pickbit.library.dto.PageResponse;
import com.pickbit.library.dto.PageableRequest;
import com.pickbit.paymentservice.api.dto.request.PaymentConfirmRequest;
import com.pickbit.paymentservice.api.dto.request.PaymentRefundRequest;
import com.pickbit.paymentservice.api.dto.request.PaymentSearchCondition;
import com.pickbit.paymentservice.api.dto.response.PaymentDetailResponse;
import com.pickbit.paymentservice.api.dto.response.PaymentRequestInfoResponse;
import com.pickbit.paymentservice.api.dto.response.PaymentSummaryResponse;
import com.pickbit.paymentservice.application.PaymentCommandService;
import com.pickbit.paymentservice.application.PaymentQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 클라이언트용 결제 API 컨트롤러.
 *
 * <p>내 결제 목록, 결제 상세, 결제 요청 정보 조회와 결제 승인/환불 기능을 제공합니다.
 */
@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;

    /**
     * 인증 사용자의 결제 목록을 페이징 조회합니다.
     *
     * @param condition 결제 검색 조건
     * @param pageableRequest 페이징 및 정렬 조건
     * @return 내 결제 요약 목록 (페이징)
     */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<PaymentSummaryResponse>> getMyPayments(
            @ModelAttribute PaymentSearchCondition condition,
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(PageResponse.from(paymentQueryService.getMyPayments(
                AuthContextHolder.getUserId(), condition, pageableRequest.toPageable(20))));
    }

    /**
     * 결제 상세 정보를 조회합니다.
     *
     * @param paymentId 조회할 결제 ID
     * @return 결제 상세 정보
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentQueryService.getPayment(AuthContextHolder.getUserId(), paymentId));
    }

    /**
     * PG 결제 요청에 필요한 정보를 조회합니다.
     *
     * @param paymentId 결제 요청 정보를 조회할 결제 ID
     * @return PG 결제 요청 정보
     */
    @GetMapping("/{paymentId}/request-info")
    public ResponseEntity<PaymentRequestInfoResponse> getRequestInfo(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentQueryService.getRequestInfo(AuthContextHolder.getUserId(), paymentId));
    }

    /**
     * PG 결제 승인을 처리합니다.
     *
     * @param request 결제 승인 요청 정보
     * @return 승인 처리된 결제 상세 정보
     */
    @PostMapping("/confirm")
    public ResponseEntity<PaymentDetailResponse> confirm(@Valid @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(paymentCommandService.confirm(AuthContextHolder.getUserId(), request));
    }

    /**
     * 결제를 환불 처리합니다.
     *
     * @param paymentId 환불할 결제 ID
     * @param request 환불 요청 정보
     * @return 환불 처리된 결제 상세 정보
     */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentDetailResponse> refund(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentRefundRequest request
    ) {
        return ResponseEntity.ok(paymentCommandService.refund(
                AuthContextHolder.getUserId(), paymentId, request.reason()));
    }

    /**
     * 결제 전 낙찰자의 결제 의무를 포기 처리합니다.
     *
     * <p>{@code REQUESTED} 상태의 결제만 포기할 수 있습니다.
     *
     * @param paymentId 포기할 결제 ID
     * @return 포기 처리된 결제 상세 정보
     */
    @PostMapping("/{paymentId}/cancel-before-pay")
    public ResponseEntity<PaymentDetailResponse> cancelBeforePayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentCommandService.cancelBeforePayment(AuthContextHolder.getUserId(), paymentId));
    }
}
