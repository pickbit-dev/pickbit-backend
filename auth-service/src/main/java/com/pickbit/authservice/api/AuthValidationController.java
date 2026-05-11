package com.pickbit.authservice.api;

import com.pickbit.authservice.api.dto.request.ValidateTokenRequest;
import com.pickbit.authservice.api.dto.response.ValidateTokenResponse;
import com.pickbit.authservice.application.query.AuthQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthValidationController {

    private final AuthQueryService authQueryService;

    @PostMapping("/validate")
    public ValidateTokenResponse validate(@Valid @RequestBody ValidateTokenRequest request) {
        return authQueryService.validate(request);
    }
}
