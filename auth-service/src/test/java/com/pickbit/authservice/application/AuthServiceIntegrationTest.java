package com.pickbit.authservice.application;

import com.pickbit.authservice.api.dto.request.LoginRequest;
import com.pickbit.authservice.api.dto.request.LogoutRequest;
import com.pickbit.authservice.api.dto.request.OAuthExchangeRequest;
import com.pickbit.authservice.api.dto.request.OAuthSignupCompleteRequest;
import com.pickbit.authservice.api.dto.request.RefreshRequest;
import com.pickbit.authservice.api.dto.request.SignupRequest;
import com.pickbit.authservice.api.dto.request.ValidateTokenRequest;
import com.pickbit.authservice.api.dto.response.AuthAccountResponse;
import com.pickbit.authservice.api.dto.response.TokenResponse;
import com.pickbit.authservice.api.dto.response.ValidateTokenResponse;
import com.pickbit.authservice.application.command.AuthCommandService;
import com.pickbit.authservice.application.event.UserNicknameEventHandler;
import com.pickbit.authservice.application.query.AuthQueryService;
import com.pickbit.authservice.config.TestContainerConfig;
import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.domain.OutBoxEvent;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.domain.enums.Role;
import com.pickbit.authservice.exception.DuplicateEmailException;
import com.pickbit.authservice.exception.DuplicateNicknameException;
import com.pickbit.authservice.exception.InvalidCredentialException;
import com.pickbit.authservice.exception.InvalidTokenException;
import com.pickbit.authservice.infrastructure.persistence.AuthAccountRepository;
import com.pickbit.authservice.infrastructure.persistence.InboxRepository;
import com.pickbit.authservice.infrastructure.persistence.OutBoxEventRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthExchangeCodeRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthSignupCodeRepository;
import com.pickbit.authservice.infrastructure.redis.RefreshTokenRedisRepository;
import com.pickbit.authservice.security.oauth.OAuthLoginResult;
import com.pickbit.authservice.security.oauth.OAuthSignupContext;
import com.pickbit.authservice.security.oauth.OAuthUserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestContainerConfig.class)
@Testcontainers
class AuthServiceIntegrationTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123";
    private static final String NICKNAME = "tester";

    @Autowired
    private AuthCommandService authCommandService;

    @Autowired
    private AuthQueryService authQueryService;

    @Autowired
    private UserNicknameEventHandler userNicknameEventHandler;

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private InboxRepository inboxRepository;

    @Autowired
    private OutBoxEventRepository outBoxEventRepository;

    @Autowired
    private RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Autowired
    private OAuthExchangeCodeRepository exchangeCodeRepository;

    @Autowired
    private OAuthSignupCodeRepository signupCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SignupRequest signupRequest() {
        return new SignupRequest(EMAIL, PASSWORD, NICKNAME);
    }

    private AuthAccount signup() {
        authCommandService.signup(signupRequest());
        return authAccountRepository.findByEmailAndOauthProvider(EMAIL, OAuthProvider.LOCAL).orElseThrow();
    }

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("정상 요청 시 계정이 저장되고 응답을 반환한다")
        void signup_success() {
            AuthAccountResponse response = authCommandService.signup(signupRequest());

            assertThat(response.accountId()).isNotNull();
            assertThat(response.email()).isEqualTo(EMAIL);
            assertThat(response.provider()).isEqualTo(OAuthProvider.LOCAL);
            assertThat(response.role()).isEqualTo(Role.USER);
            assertThat(authAccountRepository.existsById(response.accountId())).isTrue();
        }

        @Test
        @DisplayName("비밀번호는 BCrypt로 암호화되어 저장된다")
        void signup_encodesPassword() {
            AuthAccount account = signup();

            assertThat(account.getPassword()).isNotEqualTo(PASSWORD);
            assertThat(passwordEncoder.matches(PASSWORD, account.getPassword())).isTrue();
        }

        @Test
        @DisplayName("기본 계정 상태는 LOCAL, USER, enabled=true다")
        void signup_defaultAccountState() {
            AuthAccount account = signup();

            assertThat(account.getOauthProvider()).isEqualTo(OAuthProvider.LOCAL);
            assertThat(account.getRole()).isEqualTo(Role.USER);
            assertThat(account.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("회원가입 시 outbox 이벤트가 저장된다")
        void signup_recordsOutboxEvent() {
            AuthAccountResponse response = authCommandService.signup(signupRequest());

            assertThat(outBoxEventRepository.findAll()).hasSize(1);
            OutBoxEvent event = outBoxEventRepository.findAll().getFirst();
            assertThat(event.getEntity()).isEqualTo("AuthAccount");
            assertThat(event.getEventType()).isEqualTo("SIGNUP");
            assertThat(event.getAggregateId()).isEqualTo("AuthAccount:" + response.accountId());
            assertThat(event.getPayload()).contains(EMAIL, NICKNAME, "LOCAL", "USER");
        }

        @Test
        @DisplayName("중복 이메일로 가입하면 DuplicateEmailException이 발생한다")
        void signup_duplicateEmail() {
            authCommandService.signup(signupRequest());

            assertThatThrownBy(() -> authCommandService.signup(signupRequest()))
                    .isInstanceOf(DuplicateEmailException.class);
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("정상 요청 시 access token과 refresh token을 발급한다")
        void login_success() {
            signup();

            TokenResponse response = authCommandService.login(new LoginRequest(EMAIL, PASSWORD));

            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.accessTokenExpiresIn()).isPositive();
            assertThat(response.refreshTokenExpiresIn()).isPositive();
        }

        @Test
        @DisplayName("로그인 성공 시 refresh token이 Redis에 저장된다")
        void login_savesRefreshTokenToRedis() {
            AuthAccount account = signup();

            TokenResponse response = authCommandService.login(new LoginRequest(EMAIL, PASSWORD));

            assertThat(refreshTokenRedisRepository.findByAccountId(account.getId()))
                    .contains(response.refreshToken());
        }

        @Test
        @DisplayName("로그인 성공 시 lastLoginAt이 갱신된다")
        void login_recordsLastLoginAt() {
            AuthAccount account = signup();
            assertThat(account.getLastLoginAt()).isNull();

            authCommandService.login(new LoginRequest(EMAIL, PASSWORD));

            assertThat(account.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("비밀번호가 틀리면 InvalidCredentialException이 발생한다")
        void login_invalidPassword() {
            signup();

            assertThatThrownBy(() -> authCommandService.login(new LoginRequest(EMAIL, "wrong-password")))
                    .isInstanceOf(InvalidCredentialException.class);
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 InvalidCredentialException이 발생한다")
        void login_unknownEmail() {
            assertThatThrownBy(() -> authCommandService.login(new LoginRequest("unknown@example.com", PASSWORD)))
                    .isInstanceOf(InvalidCredentialException.class);
        }
    }

    @Nested
    @DisplayName("토큰")
    class Token {

        @Test
        @DisplayName("발급된 access token을 검증할 수 있다")
        void validate_success() {
            AuthAccount account = signup();
            TokenResponse token = authCommandService.login(new LoginRequest(EMAIL, PASSWORD));

            ValidateTokenResponse response = authQueryService.validate(new ValidateTokenRequest(token.accessToken()));

            assertThat(response.accountId()).isEqualTo(account.getId());
            assertThat(response.email()).isEqualTo(EMAIL);
            assertThat(response.role()).isEqualTo(Role.USER);
            assertThat(response.provider()).isEqualTo(OAuthProvider.LOCAL);
            assertThat(response.expiresAt()).isNotNull();
        }

        @Test
        @DisplayName("저장된 refresh token으로 토큰을 재발급할 수 있다")
        void refresh_success() {
            signup();
            TokenResponse token = authCommandService.login(new LoginRequest(EMAIL, PASSWORD));

            TokenResponse response = authCommandService.refresh(new RefreshRequest(token.refreshToken()));

            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.tokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("Redis에 저장된 refresh token과 다르면 InvalidTokenException이 발생한다")
        void refresh_mismatchedRefreshToken() {
            AuthAccount account = signup();
            TokenResponse token = authCommandService.login(new LoginRequest(EMAIL, PASSWORD));
            refreshTokenRedisRepository.save(account.getId(), "different-refresh-token", java.time.Duration.ofMinutes(10));

            assertThatThrownBy(() -> authCommandService.refresh(new RefreshRequest(token.refreshToken())))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("로그아웃 시 Redis의 refresh token이 삭제된다")
        void logout_deletesRefreshToken() {
            AuthAccount account = signup();
            TokenResponse token = authCommandService.login(new LoginRequest(EMAIL, PASSWORD));
            assertThat(refreshTokenRedisRepository.findByAccountId(account.getId())).isPresent();

            authCommandService.logout(new LogoutRequest(token.refreshToken()));

            assertThat(refreshTokenRedisRepository.findByAccountId(account.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("사용자 이벤트 동기화")
    class UserEventSync {

        @Test
        @DisplayName("닉네임 변경 이벤트를 처리하면 AuthAccount 닉네임이 갱신된다")
        void nicknameUpdatedEvent_updatesAuthAccount() {
            AuthAccount account = signup();
            String eventId = "user-service-test-event";
            String aggregateId = "User:" + account.getId();
            String messageBody = """
                    {"eventId":"%s","accountId":%d,"nickname":"changed","updatedAt":"2026-05-13T00:00:00"}
                    """.formatted(eventId, account.getId());

            userNicknameEventHandler.handleNicknameUpdated(eventId, aggregateId, messageBody);

            assertThat(account.getNickname()).isEqualTo("changed");
            assertThat(inboxRepository.existsByEventId(eventId)).isTrue();
        }
    }

    @Nested
    @DisplayName("OAuth 로그인")
    class OAuthLogin {

        @Test
        @DisplayName("신규 OAuth 사용자면 추가 가입 정보 입력이 필요하다")
        void oauthLogin_newUserRequiresSignup() {
            OAuthUserInfo userInfo = new OAuthUserInfo(OAuthProvider.KAKAO, "kakao-1", null, null);

            OAuthLoginResult result = authCommandService.oauthLogin(userInfo);

            assertThat(result.requiresSignup()).isTrue();
            assertThat(result.signupContext().provider()).isEqualTo(OAuthProvider.KAKAO);
            assertThat(result.signupContext().providerId()).isEqualTo("kakao-1");
            assertThat(result.signupContext().email()).isNull();
            assertThat(result.signupContext().nickname()).isNull();
            assertThat(authAccountRepository.findByOauthProviderAndOauthProviderId(OAuthProvider.KAKAO, "kakao-1")).isEmpty();
        }

        @Test
        @DisplayName("OAuth 추가 가입을 완료하면 계정을 저장하고 토큰을 발급한다")
        void completeOAuthSignup_createsAccount() {
            signupCodeRepository.save(
                    "signup-code-create",
                    new OAuthSignupContext(OAuthProvider.KAKAO, "kakao-1", "provider@example.com", "제공닉네임"),
                    Duration.ofMinutes(10)
            );

            TokenResponse response = authCommandService.completeOAuthSignup(
                    new OAuthSignupCompleteRequest("signup-code-create", "kakao@example.com", "카카오유저")
            );

            AuthAccount account = authAccountRepository.findByOauthProviderAndOauthProviderId(OAuthProvider.KAKAO, "kakao-1")
                    .orElseThrow();
            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(account.getEmail()).isEqualTo("kakao@example.com");
            assertThat(account.getPassword()).isNull();
            assertThat(account.getOauthProvider()).isEqualTo(OAuthProvider.KAKAO);
            assertThat(account.getOauthProviderId()).isEqualTo("kakao-1");
            assertThat(account.getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("OAuth 추가 가입 완료 시 outbox 이벤트가 저장된다")
        void completeOAuthSignup_recordsOutboxEvent() {
            signupCodeRepository.save(
                    "signup-code-outbox",
                    new OAuthSignupContext(OAuthProvider.GOOGLE, "google-1", "provider@example.com", "제공닉네임"),
                    Duration.ofMinutes(10)
            );

            authCommandService.completeOAuthSignup(
                    new OAuthSignupCompleteRequest("signup-code-outbox", "google@example.com", "구글유저")
            );

            assertThat(outBoxEventRepository.findAll()).hasSize(1);
            OutBoxEvent event = outBoxEventRepository.findAll().getFirst();
            assertThat(event.getEventType()).isEqualTo("SIGNUP");
            assertThat(event.getPayload()).contains("google@example.com", "구글유저", "GOOGLE", "USER");
        }

        @Test
        @DisplayName("기존 OAuth 계정이면 새 계정을 만들지 않고 로그인 처리한다")
        void oauthLogin_existingAccount() {
            OAuthUserInfo userInfo = new OAuthUserInfo(OAuthProvider.NAVER, "naver-1", "naver@example.com", "네이버유저");
            signupCodeRepository.save(
                    "signup-code-existing",
                    OAuthSignupContext.from(userInfo),
                    Duration.ofMinutes(10)
            );
            authCommandService.completeOAuthSignup(
                    new OAuthSignupCompleteRequest("signup-code-existing", "naver@example.com", "네이버유저")
            );
            outBoxEventRepository.deleteAll();

            OAuthLoginResult result = authCommandService.oauthLogin(userInfo);

            assertThat(result.requiresSignup()).isFalse();
            assertThat(result.tokenResponse().accessToken()).isNotBlank();
            assertThat(authAccountRepository.findAll()).hasSize(1);
            assertThat(outBoxEventRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("OAuth 추가 가입 시 이메일이 중복되면 DuplicateEmailException이 발생한다")
        void completeOAuthSignup_duplicateEmail() {
            signup();
            signupCodeRepository.save(
                    "signup-code-duplicate-email",
                    new OAuthSignupContext(OAuthProvider.KAKAO, "kakao-duplicate-email", null, null),
                    Duration.ofMinutes(10)
            );

            assertThatThrownBy(() -> authCommandService.completeOAuthSignup(
                    new OAuthSignupCompleteRequest("signup-code-duplicate-email", EMAIL, "새닉네임")
            )).isInstanceOf(DuplicateEmailException.class);
        }

        @Test
        @DisplayName("OAuth 추가 가입 시 닉네임이 중복되면 DuplicateNicknameException이 발생한다")
        void completeOAuthSignup_duplicateNickname() {
            signup();
            signupCodeRepository.save(
                    "signup-code-duplicate-nickname",
                    new OAuthSignupContext(OAuthProvider.KAKAO, "kakao-duplicate-nickname", null, null),
                    Duration.ofMinutes(10)
            );

            assertThatThrownBy(() -> authCommandService.completeOAuthSignup(
                    new OAuthSignupCompleteRequest("signup-code-duplicate-nickname", "new@example.com", NICKNAME)
            )).isInstanceOf(DuplicateNicknameException.class);
        }

        @Test
        @DisplayName("OAuth exchange code로 토큰을 한 번만 교환할 수 있다")
        void exchangeOAuthCode_consumesOnce() {
            TokenResponse token = TokenResponse.bearer("access", "refresh", 1000, 2000);
            exchangeCodeRepository.save("exchange-code", token, Duration.ofMinutes(3));

            TokenResponse response = authCommandService.exchangeOAuthCode(new OAuthExchangeRequest("exchange-code"));

            assertThat(response).isEqualTo(token);
            assertThatThrownBy(() -> authCommandService.exchangeOAuthCode(new OAuthExchangeRequest("exchange-code")))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("존재하지 않는 OAuth exchange code면 InvalidTokenException이 발생한다")
        void exchangeOAuthCode_notFound() {
            assertThatThrownBy(() -> authCommandService.exchangeOAuthCode(new OAuthExchangeRequest("missing-code")))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
