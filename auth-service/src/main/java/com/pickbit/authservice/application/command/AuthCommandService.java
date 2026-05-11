package com.pickbit.authservice.application.command;

import com.pickbit.authservice.api.dto.request.LoginRequest;
import com.pickbit.authservice.api.dto.request.LogoutRequest;
import com.pickbit.authservice.api.dto.request.OAuthExchangeRequest;
import com.pickbit.authservice.api.dto.request.RefreshRequest;
import com.pickbit.authservice.api.dto.request.SignupRequest;
import com.pickbit.authservice.api.dto.response.AuthAccountResponse;
import com.pickbit.authservice.api.dto.response.TokenResponse;
import com.pickbit.authservice.application.OutboxRecorder;
import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.exception.DuplicateEmailException;
import com.pickbit.authservice.exception.InvalidCredentialException;
import com.pickbit.authservice.exception.InvalidTokenException;
import com.pickbit.authservice.infrastructure.persistence.AuthAccountRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthExchangeCodeRepository;
import com.pickbit.authservice.infrastructure.redis.RefreshTokenRedisRepository;
import com.pickbit.authservice.security.JwtTokenProvider;
import com.pickbit.authservice.security.oauth.OAuthUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthCommandService {

    private final AuthAccountRepository authAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final OAuthExchangeCodeRepository exchangeCodeRepository;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public AuthAccountResponse signup(SignupRequest request) {
        if (authAccountRepository.existsByEmailAndOauthProvider(request.email(), OAuthProvider.LOCAL)) {
            throw new DuplicateEmailException(request.email());
        }

        AuthAccount account = AuthAccount.local(request.email(), passwordEncoder.encode(request.password()));
        AuthAccount saved = authAccountRepository.save(account);
        outboxRecorder.signupEvent(saved, request.nickname());
        return AuthAccountResponse.from(saved);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AuthAccount account = authAccountRepository.findByEmailAndOauthProvider(request.email(), OAuthProvider.LOCAL)
                .filter(AuthAccount::getEnabled)
                .orElseThrow(InvalidCredentialException::new);

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new InvalidCredentialException();
        }

        account.recordLogin();
        return issueTokens(account);
    }

    @Transactional
    public TokenResponse oauthLogin(OAuthUserInfo userInfo) {
        AuthAccount account = authAccountRepository
                .findByOauthProviderAndOauthProviderId(userInfo.provider(), userInfo.providerId())
                .orElseGet(() -> createOAuthAccount(userInfo));

        if (!account.getEnabled()) {
            throw new InvalidCredentialException();
        }

        account.recordLogin();
        return issueTokens(account);
    }

    @Transactional
    public TokenResponse exchangeOAuthCode(OAuthExchangeRequest request) {
        return exchangeCodeRepository.consume(request.code())
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 OAuth exchange code입니다."));
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Long accountId = jwtTokenProvider.parseRefreshTokenSubject(request.refreshToken());
        if (!refreshTokenRedisRepository.existsAndMatches(accountId, request.refreshToken())) {
            throw new InvalidTokenException("저장된 refresh token과 일치하지 않습니다.");
        }

        AuthAccount account = authAccountRepository.findById(accountId)
                .filter(AuthAccount::getEnabled)
                .orElseThrow(() -> new InvalidTokenException("토큰의 계정을 찾을 수 없습니다."));
        return issueTokens(account);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        Long accountId = jwtTokenProvider.parseRefreshTokenSubject(request.refreshToken());
        refreshTokenRedisRepository.delete(accountId);
    }

    private TokenResponse issueTokens(AuthAccount account) {
        String accessToken = jwtTokenProvider.createAccessToken(account);
        String refreshToken = jwtTokenProvider.createRefreshToken(account);
        refreshTokenRedisRepository.save(account.getId(), refreshToken, Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs()));
        return TokenResponse.bearer(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenValidityMs(),
                jwtTokenProvider.getRefreshTokenValidityMs()
        );
    }

    private AuthAccount createOAuthAccount(OAuthUserInfo userInfo) {
        AuthAccount saved = authAccountRepository.save(
                AuthAccount.oauth(userInfo.email(), userInfo.provider(), userInfo.providerId())
        );
        outboxRecorder.signupEvent(saved, userInfo.nickname());
        return saved;
    }
}
