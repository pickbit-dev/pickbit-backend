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

/**
 * 내부 서비스 간 사용자 조회 API 컨트롤러.
 *
 * <p>다른 서비스에서 사용자 정보를 조회할 때 사용하는 내부 전용 엔드포인트를 제공합니다.
 */
@Tag(name = "Internal User", description = "내부 서비스 간 사용자 조회 API")
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserQueryService userQueryService;

    /**
     * 닉네임으로 사용자 정보를 조회합니다.
     *
     * @param nickname 조회할 사용자 닉네임
     * @return 사용자 정보
     */
    @GetMapping("/by-nickname/{nickname}")
    public ResponseEntity<UserResponse> getByNickname(@PathVariable String nickname) {
        return ResponseEntity.ok(userQueryService.getByNickname(nickname));
    }
}
