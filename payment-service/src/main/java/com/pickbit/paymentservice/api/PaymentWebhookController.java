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

@Slf4j
@Tag(name = "PaymentWebhook", description = "결제 webhook 수신")
@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private static final String SIGNATURE_HEADER = "TossPayments-Signature";

    private final TossWebhookSignatureVerifier signatureVerifier;
    private final TossWebhookHandler tossWebhookHandler;

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
