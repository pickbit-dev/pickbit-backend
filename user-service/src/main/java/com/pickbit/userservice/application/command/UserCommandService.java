package com.pickbit.userservice.application.command;

import com.pickbit.userservice.api.dto.ProfileUpdateRequest;
import com.pickbit.userservice.api.dto.UserResponse;
import com.pickbit.userservice.application.OutboxRecorder;
import com.pickbit.userservice.domain.User;
import com.pickbit.userservice.exception.DuplicateNicknameException;
import com.pickbit.userservice.exception.UserNotFoundException;
import com.pickbit.userservice.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserRepository userRepository;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public UserResponse updateMe(Long accountId, ProfileUpdateRequest request) {
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new UserNotFoundException(accountId));
        if (userRepository.existsByNicknameAndAccountIdNot(request.nickname(), accountId)) {
            throw new DuplicateNicknameException(request.nickname());
        }
        user.changeProfile(request.nickname(), request.profileImageUrl());
        outboxRecorder.nicknameUpdatedEvent(user);
        return UserResponse.from(user);
    }
}
