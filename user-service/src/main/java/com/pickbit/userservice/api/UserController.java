package com.pickbit.userservice.api;

import com.pickbit.library.auth.AuthHeaders;
import com.pickbit.userservice.api.dto.ProfileUpdateRequest;
import com.pickbit.userservice.api.dto.UserResponse;
import com.pickbit.userservice.application.command.UserCommandService;
import com.pickbit.userservice.application.query.UserQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    @GetMapping("/me")
    public UserResponse getMe(@RequestHeader(AuthHeaders.USER_ID) Long accountId) {
        return userQueryService.getMe(accountId);
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
            @RequestHeader(AuthHeaders.USER_ID) Long accountId,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return userCommandService.updateMe(accountId, request);
    }

    @GetMapping("/{accountId}")
    public UserResponse getByAccountId(@PathVariable Long accountId) {
        return userQueryService.getByAccountId(accountId);
    }
}
