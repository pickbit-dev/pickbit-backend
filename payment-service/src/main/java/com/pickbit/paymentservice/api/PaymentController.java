package com.pickbit.paymentservice.api;

import com.pickbit.library.auth.AuthContextHolder;
import com.pickbit.paymentservice.api.dto.request.PaymentConfirmRequest;
import com.pickbit.paymentservice.api.dto.request.PaymentRefundRequest;
import com.pickbit.paymentservice.api.dto.response.PaymentDetailResponse;
import com.pickbit.paymentservice.api.dto.response.PaymentRequestInfoResponse;
import com.pickbit.paymentservice.application.PaymentCommandService;
import com.pickbit.paymentservice.application.PaymentQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentQueryService.getPayment(AuthContextHolder.getUserId(), paymentId));
    }

    @GetMapping("/{paymentId}/request-info")
    public ResponseEntity<PaymentRequestInfoResponse> getRequestInfo(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentQueryService.getRequestInfo(AuthContextHolder.getUserId(), paymentId));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentDetailResponse> confirm(@Valid @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(paymentCommandService.confirm(AuthContextHolder.getUserId(), request));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentDetailResponse> refund(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentRefundRequest request
    ) {
        return ResponseEntity.ok(paymentCommandService.refund(
                AuthContextHolder.getUserId(), paymentId, request.reason()));
    }
}
