package com.pickbit.userservice.api;

import com.pickbit.library.auth.AuthContextHolder;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    @GetMapping("/me")
    public UserResponse getMe() {
        return userQueryService.getMe(AuthContextHolder.getUserId());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return userCommandService.updateMe(AuthContextHolder.getUserId(), request);
    }

    @GetMapping("/{accountId}")
    public UserResponse getByAccountId(@PathVariable Long accountId) {
        return userQueryService.getByAccountId(accountId);
    }
}
