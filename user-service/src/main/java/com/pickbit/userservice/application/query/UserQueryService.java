package com.pickbit.userservice.application.query;

import com.pickbit.userservice.api.dto.UserResponse;
import com.pickbit.userservice.exception.UserNotFoundException;
import com.pickbit.userservice.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(Long accountId) {
        return userRepository.findByAccountId(accountId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public UserResponse getByAccountId(Long accountId) {
        return getMe(accountId);
    }

    @Transactional(readOnly = true)
    public UserResponse getByNickname(String nickname) {
        return userRepository.findByNickname(nickname)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(nickname));
    }
}
