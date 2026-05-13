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

/**
 * 사용자 프로필 조회 및 수정 API를 제공하는 컨트롤러입니다.
 * <p>
 * 로그인 사용자의 식별자는 Gateway가 전달한 인증 컨텍스트에서 조회합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    /**
     * 현재 로그인한 사용자의 프로필을 조회합니다.
     *
     * @return 현재 로그인한 사용자 프로필 정보
     */
    @GetMapping("/me")
    public UserResponse getMe() {
        return userQueryService.getMe(AuthContextHolder.getUserId());
    }

    /**
     * 현재 로그인한 사용자의 닉네임과 프로필 이미지를 수정합니다.
     *
     * @param request 수정할 닉네임과 프로필 이미지 URL
     * @return 수정된 사용자 프로필 정보
     */
    @PatchMapping("/me")
    public UserResponse updateMe(
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return userCommandService.updateMe(AuthContextHolder.getUserId(), request);
    }

    /**
     * 인증 계정 ID로 사용자 프로필을 조회합니다.
     *
     * @param accountId auth-service의 인증 계정 ID
     * @return 사용자 프로필 정보
     */
    @GetMapping("/{accountId}")
    public UserResponse getByAccountId(@PathVariable Long accountId) {
        return userQueryService.getByAccountId(accountId);
    }
}
