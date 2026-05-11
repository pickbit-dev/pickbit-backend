package com.pickbit.authservice.api;

import com.pickbit.authservice.api.dto.request.OAuthExchangeRequest;
import com.pickbit.authservice.api.dto.response.TokenResponse;
import com.pickbit.authservice.application.command.AuthCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/oauth")
public class OAuthController {

    private final AuthCommandService authCommandService;

    @PostMapping("/exchange")
    public TokenResponse exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return authCommandService.exchangeOAuthCode(request);
    }
}
