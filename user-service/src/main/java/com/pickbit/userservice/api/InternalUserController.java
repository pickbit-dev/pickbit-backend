package com.pickbit.userservice.api;

import com.pickbit.userservice.api.dto.UserResponse;
import com.pickbit.userservice.application.query.UserQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal User", description = "내부 서비스 간 사용자 조회 API")
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserQueryService userQueryService;

    @GetMapping("/by-nickname/{nickname}")
    public ResponseEntity<UserResponse> getByNickname(@PathVariable String nickname) {
        return ResponseEntity.ok(userQueryService.getByNickname(nickname));
    }
}
