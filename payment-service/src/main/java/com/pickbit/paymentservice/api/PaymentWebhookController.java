package com.pickbit.paymentservice.api;

import com.pickbit.paymentservice.application.TossWebhookHandler;
import com.pickbit.paymentservice.application.TossWebhookSignatureVerifier;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스페이먼츠 결제 webhook 수신 컨트롤러.
 *
 * <p>외부 PG에서 전달한 webhook 서명을 검증하고 결제 이벤트를 처리합니다.
 */
@Slf4j
@Tag(name = "PaymentWebhook", description = "결제 webhook 수신")
@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private static final String SIGNATURE_HEADER = "TossPayments-Signature";

    private final TossWebhookSignatureVerifier signatureVerifier;
    private final TossWebhookHandler tossWebhookHandler;
    /**
     * 토스페이먼츠 webhook을 수신합니다.
     *
     * @param signature 토스페이먼츠 webhook 서명 헤더
     * @param rawBody 원본 webhook 요청 본문
     * @param request servlet 요청 객체
     * @return 응답 본문 없음
     */
    @PostMapping("/toss")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String rawBody,
            HttpServletRequest request
    ) {
        signatureVerifier.verify(signature, rawBody);
        tossWebhookHandler.handle(rawBody);
        return ResponseEntity.ok().build();
    }
}
