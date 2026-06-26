package com.pickbit.authservice.application.command;

import com.pickbit.authservice.api.dto.request.LoginRequest;
import com.pickbit.authservice.api.dto.request.LogoutRequest;
import com.pickbit.authservice.api.dto.request.OAuthExchangeRequest;
import com.pickbit.authservice.api.dto.request.OAuthLinkRequest;
import com.pickbit.authservice.api.dto.request.OAuthSignupCompleteRequest;
import com.pickbit.authservice.api.dto.request.RefreshRequest;
import com.pickbit.authservice.api.dto.request.SignupRequest;
import com.pickbit.authservice.api.dto.response.AuthAccountResponse;
import com.pickbit.authservice.api.dto.response.OAuthLinkContextResponse;
import com.pickbit.authservice.api.dto.response.OAuthSignupContextResponse;
import com.pickbit.authservice.api.dto.response.TokenResponse;
import com.pickbit.authservice.application.OutboxRecorder;
import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.domain.AuthProviderLink;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.exception.DuplicateEmailException;
import com.pickbit.authservice.exception.DuplicateNicknameException;
import com.pickbit.authservice.exception.InvalidCredentialException;
import com.pickbit.authservice.exception.InvalidTokenException;
import com.pickbit.authservice.infrastructure.persistence.AuthAccountRepository;
import com.pickbit.authservice.infrastructure.persistence.AuthProviderLinkRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthExchangeCodeRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthLinkCodeRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthSignupCodeRepository;
import com.pickbit.authservice.infrastructure.redis.RefreshTokenRedisRepository;
import com.pickbit.authservice.security.JwtTokenProvider;
import com.pickbit.authservice.security.oauth.OAuthLinkContext;
import com.pickbit.authservice.security.oauth.OAuthLoginResult;
import com.pickbit.authservice.security.oauth.OAuthSignupContext;
import com.pickbit.authservice.security.oauth.OAuthUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthCommandService {

    private final AuthAccountRepository authAccountRepository;
    private final AuthProviderLinkRepository authProviderLinkRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final OAuthExchangeCodeRepository exchangeCodeRepository;
    private final OAuthSignupCodeRepository signupCodeRepository;
    private final OAuthLinkCodeRepository linkCodeRepository;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public AuthAccountResponse signup(SignupRequest request) {
        String normalizedNickname = AuthAccount.normalizeNickname(request.nickname(), request.email());
        if (authAccountRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        if (authAccountRepository.existsByNickname(normalizedNickname)) {
            throw new DuplicateNicknameException(normalizedNickname);
        }

        AuthAccount account = AuthAccount.local(request.email(), passwordEncoder.encode(request.password()), normalizedNickname);
        AuthAccount saved = authAccountRepository.save(account);
        outboxRecorder.signupEvent(saved, normalizedNickname, OAuthProvider.LOCAL);
        return AuthAccountResponse.from(saved, OAuthProvider.LOCAL);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AuthAccount account = authAccountRepository.findByEmail(request.email())
                .filter(AuthAccount::getEnabled)
                .filter(candidate -> StringUtils.hasText(candidate.getPassword()))
                .orElseThrow(InvalidCredentialException::new);

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new InvalidCredentialException();
        }

        account.recordLogin();
        return issueTokens(account, OAuthProvider.LOCAL);
    }

    @Transactional
    public OAuthLoginResult oauthLogin(OAuthUserInfo userInfo) {
        return authProviderLinkRepository
                .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
                .map(AuthProviderLink::getAccount)
                .map(account -> {
                    if (!account.getEnabled()) {
                        throw new InvalidCredentialException();
                    }
                    account.recordLogin();
                    return OAuthLoginResult.authenticated(issueTokens(account, userInfo.provider()));
                })
                .orElseGet(() -> {
                    if (StringUtils.hasText(userInfo.email())) {
                        return authAccountRepository.findByEmail(userInfo.email())
                                .map(account -> OAuthLoginResult.linkRequired(OAuthLinkContext.from(account.getId(), userInfo)))
                                .orElseGet(() -> OAuthLoginResult.signupRequired(OAuthSignupContext.from(userInfo)));
                    }
                    return OAuthLoginResult.manualResolutionRequired(
                            OAuthSignupContext.from(userInfo),
                            OAuthLinkContext.manual(userInfo)
                    );
                });
    }

    @Transactional(readOnly = true)
    public OAuthSignupContextResponse getOAuthSignupContext(String code) {
        OAuthSignupContext context = signupCodeRepository.find(code)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 OAuth signup code입니다."));
        return OAuthSignupContextResponse.from(context);
    }

    @Transactional(readOnly = true)
    public OAuthLinkContextResponse getOAuthLinkContext(String code) {
        OAuthLinkContext context = linkCodeRepository.find(code)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 OAuth link code입니다."));
        return OAuthLinkContextResponse.from(context);
    }

    @Transactional
    public TokenResponse completeOAuthSignup(OAuthSignupCompleteRequest request) {
        OAuthSignupContext context = signupCodeRepository.consume(request.code())
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 OAuth signup code입니다."));

        String normalizedNickname = AuthAccount.normalizeNickname(request.nickname(), request.email());
        validateOAuthSignup(context, request, normalizedNickname);

        AuthAccount account = AuthAccount.oauth(request.email(), normalizedNickname);
        AuthAccount saved = authAccountRepository.save(account);
        authProviderLinkRepository.save(AuthProviderLink.create(saved, context.provider(), context.providerId()));
        outboxRecorder.signupEvent(saved, normalizedNickname, context.provider());
        saved.recordLogin();
        return issueTokens(saved, context.provider());
    }

    @Transactional
    public TokenResponse linkOAuthAccount(OAuthLinkRequest request) {
        OAuthLinkContext context = linkCodeRepository.consume(request.code())
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 OAuth link code입니다."));

        AuthAccount account = findLinkTargetAccount(context, request.email())
                .filter(AuthAccount::getEnabled)
                .orElseThrow(InvalidCredentialException::new);
        if (!account.getEmail().equals(request.email()) || !StringUtils.hasText(account.getPassword())) {
            throw new InvalidCredentialException();
        }
        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new InvalidCredentialException();
        }
        if (authProviderLinkRepository.existsByProviderAndProviderId(context.provider(), context.providerId())
                || authProviderLinkRepository.existsByAccountIdAndProvider(account.getId(), context.provider())) {
            throw new DuplicateEmailException(account.getEmail());
        }

        authProviderLinkRepository.save(AuthProviderLink.create(account, context.provider(), context.providerId()));
        account.recordLogin();
        return issueTokens(account, context.provider());
    }

    private java.util.Optional<AuthAccount> findLinkTargetAccount(OAuthLinkContext context, String email) {
        if (context.accountId() != null) {
            return authAccountRepository.findById(context.accountId());
        }
        return authAccountRepository.findByEmail(email);
    }

    @Transactional
    public TokenResponse exchangeOAuthCode(OAuthExchangeRequest request) {
        return exchangeCodeRepository.consume(request.code())
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 OAuth exchange code입니다."));
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        return refresh(request.refreshToken());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new InvalidTokenException("refresh token이 필요합니다.");
        }
        Long accountId = jwtTokenProvider.parseRefreshTokenSubject(refreshToken);
        if (!refreshTokenRedisRepository.existsAndMatches(accountId, refreshToken)) {
            throw new InvalidTokenException("저장된 refresh token과 일치하지 않습니다.");
        }

        AuthAccount account = authAccountRepository.findById(accountId)
                .filter(AuthAccount::getEnabled)
                .orElseThrow(() -> new InvalidTokenException("토큰의 계정을 찾을 수 없습니다."));
        return issueTokens(account, jwtTokenProvider.parseRefreshTokenProvider(refreshToken));
    }

    @Transactional
    public void logout(LogoutRequest request) {
        logout(request.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new InvalidTokenException("refresh token이 필요합니다.");
        }
        Long accountId = jwtTokenProvider.parseRefreshTokenSubject(refreshToken);
        refreshTokenRedisRepository.delete(accountId);
    }

    private TokenResponse issueTokens(AuthAccount account, OAuthProvider loginProvider) {
        String accessToken = jwtTokenProvider.createAccessToken(account, loginProvider);
        String refreshToken = jwtTokenProvider.createRefreshToken(account, loginProvider);
        refreshTokenRedisRepository.save(account.getId(), refreshToken, Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs()));
        return TokenResponse.bearer(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenValidityMs(),
                jwtTokenProvider.getRefreshTokenValidityMs()
        );
    }

    private void validateOAuthSignup(OAuthSignupContext context, OAuthSignupCompleteRequest request, String normalizedNickname) {
        if (authProviderLinkRepository.existsByProviderAndProviderId(context.provider(), context.providerId())) {
            throw new DuplicateEmailException(request.email());
        }
        if (authAccountRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        if (authAccountRepository.existsByNickname(normalizedNickname)) {
            throw new DuplicateNicknameException(normalizedNickname);
        }
    }
}
