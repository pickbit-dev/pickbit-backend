package com.pickbit.authservice.application;

import com.pickbit.authservice.api.dto.request.LoginRequest;
import com.pickbit.authservice.api.dto.request.LogoutRequest;
import com.pickbit.authservice.api.dto.request.OAuthExchangeRequest;
import com.pickbit.authservice.api.dto.request.OAuthLinkRequest;
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
import com.pickbit.authservice.domain.AuthProviderLink;
import com.pickbit.authservice.domain.OutBoxEvent;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.domain.enums.Role;
import com.pickbit.authservice.exception.DuplicateEmailException;
import com.pickbit.authservice.exception.DuplicateNicknameException;
import com.pickbit.authservice.exception.InvalidCredentialException;
import com.pickbit.authservice.exception.InvalidTokenException;
import com.pickbit.authservice.infrastructure.persistence.AuthAccountRepository;
import com.pickbit.authservice.infrastructure.persistence.AuthProviderLinkRepository;
import com.pickbit.authservice.infrastructure.persistence.InboxRepository;
import com.pickbit.authservice.infrastructure.persistence.OutBoxEventRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthExchangeCodeRepository;
import com.pickbit.authservice.infrastructure.redis.OAuthLinkCodeRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@Import(TestContainerConfig.class)
@ActiveProfiles("test")
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
    private AuthProviderLinkRepository authProviderLinkRepository;

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
    private OAuthLinkCodeRepository linkCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private SignupRequest signupRequest() {
        return new SignupRequest(EMAIL, PASSWORD, NICKNAME);
    }

    private AuthAccount signup() {
        authCommandService.signup(signupRequest());
        return authAccountRepository.findByEmail(EMAIL).orElseThrow();
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
        @DisplayName("기본 계정 상태는 USER, enabled=true다")
        void signup_defaultAccountState() {
            AuthAccount account = signup();

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
        @DisplayName("회원가입 닉네임은 공백을 제거해서 저장하고 이벤트에도 같은 값을 기록한다")
        void signup_normalizesNicknameWhitespace() {
            AuthAccountResponse response = authCommandService.signup(
                    new SignupRequest("space@example.com", PASSWORD, "Mr. Stanley Toy"));

            AuthAccount account = authAccountRepository.findById(response.accountId()).orElseThrow();
            OutBoxEvent event = outBoxEventRepository.findAll().getFirst();

            assertThat(account.getNickname()).isEqualTo("Mr.StanleyToy");
            assertThat(event.getPayload()).contains("Mr.StanleyToy");
            assertThat(event.getPayload()).doesNotContain("Mr. Stanley Toy");
        }

        @Test
        @DisplayName("공백만 다른 닉네임은 중복으로 처리한다")
        void signup_duplicateNormalizedNickname() {
            authCommandService.signup(new SignupRequest("first@example.com", PASSWORD, "Mr.StanleyToy"));

            assertThatThrownBy(() -> authCommandService.signup(
                    new SignupRequest("second@example.com", PASSWORD, "Mr. Stanley Toy")))
                    .isInstanceOf(DuplicateNicknameException.class);
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
        @DisplayName("같은 refresh token으로 두 번 재발급하면 두 번째는 거부된다")
        void refresh_isSingleUse() {
            signup();
            TokenResponse token = authCommandService.login(new LoginRequest(EMAIL, PASSWORD));

            authCommandService.refresh(new RefreshRequest(token.refreshToken()));

            // 검사와 교체가 나뉘어 있던 시절에는 같은 토큰을 든 요청이 둘 다 통과했다.
            // 회전이 원자적이면 이미 교체된 뒤라 두 번째는 실패해야 한다.
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

            // eventVersion 은 아웃박스 행 ID. 커넥터 갱신 전 메시지에는 없을 수 있어 null 도 허용된다.
            userNicknameEventHandler.handleNicknameUpdated(eventId, aggregateId, messageBody, 1L);

            assertThat(account.getNickname()).isEqualTo("changed");
            assertThat(inboxRepository.existsBySuccessEventId(eventId)).isTrue();
        }
    }

    @Nested
    @DisplayName("OAuth 로그인")
    class OAuthLogin {

        @Test
        @DisplayName("이메일 없는 신규 OAuth 사용자면 가입 또는 기존 계정 연결 선택이 필요하다")
        void oauthLogin_newUserRequiresSignup() {
            OAuthUserInfo userInfo = new OAuthUserInfo(OAuthProvider.KAKAO, "kakao-1", null, null);

            OAuthLoginResult result = authCommandService.oauthLogin(userInfo);

            assertThat(result.requiresSignup()).isTrue();
            assertThat(result.requiresLink()).isTrue();
            assertThat(result.signupContext().provider()).isEqualTo(OAuthProvider.KAKAO);
            assertThat(result.signupContext().providerId()).isEqualTo("kakao-1");
            assertThat(result.signupContext().email()).isNull();
            assertThat(result.signupContext().nickname()).isNull();
            assertThat(result.linkContext().accountId()).isNull();
            assertThat(result.linkContext().provider()).isEqualTo(OAuthProvider.KAKAO);
            assertThat(result.linkContext().providerId()).isEqualTo("kakao-1");
            assertThat(authProviderLinkRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-1")).isEmpty();
        }

        @Test
        @DisplayName("이메일 없는 OAuth 연결 code는 사용자가 입력한 이메일의 로컬 계정에 연결한다")
        void linkOAuthAccount_manualEmail_success() {
            AuthAccount account = signup();
            linkCodeRepository.save(
                    "link-code-manual-email",
                    new com.pickbit.authservice.security.oauth.OAuthLinkContext(
                            null, OAuthProvider.KAKAO, "kakao-manual-email", null, null),
                    Duration.ofMinutes(10)
            );

            TokenResponse response = authCommandService.linkOAuthAccount(
                    new OAuthLinkRequest("link-code-manual-email", EMAIL, PASSWORD)
            );

            AuthProviderLink link = authProviderLinkRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-manual-email")
                    .orElseThrow();
            assertThat(response.accessToken()).isNotBlank();
            assertThat(link.getAccount().getId()).isEqualTo(account.getId());
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

            AuthProviderLink link = authProviderLinkRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-1")
                    .orElseThrow();
            AuthAccount account = link.getAccount();
            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(account.getEmail()).isEqualTo("kakao@example.com");
            assertThat(account.getPassword()).isNull();
            assertThat(link.getProvider()).isEqualTo(OAuthProvider.KAKAO);
            assertThat(link.getProviderId()).isEqualTo("kakao-1");
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
        @DisplayName("OAuth 이메일이 기존 로컬 계정과 같으면 계정 연결이 필요하다")
        void oauthLogin_existingEmailRequiresLink() {
            AuthAccount account = signup();
            OAuthUserInfo userInfo = new OAuthUserInfo(OAuthProvider.KAKAO, "kakao-link", EMAIL, "카카오닉네임");

            OAuthLoginResult result = authCommandService.oauthLogin(userInfo);

            assertThat(result.requiresLink()).isTrue();
            assertThat(result.requiresSignup()).isFalse();
            assertThat(result.linkContext().accountId()).isEqualTo(account.getId());
            assertThat(result.linkContext().provider()).isEqualTo(OAuthProvider.KAKAO);
            assertThat(result.linkContext().providerId()).isEqualTo("kakao-link");
            assertThat(authProviderLinkRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-link")).isEmpty();
        }

        @Test
        @DisplayName("기존 로컬 계정 비밀번호 인증 후 OAuth provider를 연결하고 토큰을 발급한다")
        void linkOAuthAccount_success() {
            AuthAccount account = signup();
            linkCodeRepository.save(
                    "link-code-success",
                    new com.pickbit.authservice.security.oauth.OAuthLinkContext(
                            account.getId(), OAuthProvider.KAKAO, "kakao-link-success", EMAIL, "카카오닉네임"),
                    Duration.ofMinutes(10)
            );

            TokenResponse response = authCommandService.linkOAuthAccount(
                    new OAuthLinkRequest("link-code-success", EMAIL, PASSWORD)
            );

            AuthProviderLink link = authProviderLinkRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "kakao-link-success")
                    .orElseThrow();
            assertThat(response.accessToken()).isNotBlank();
            assertThat(link.getAccount().getId()).isEqualTo(account.getId());
            assertThatThrownBy(() -> authCommandService.linkOAuthAccount(
                    new OAuthLinkRequest("link-code-success", EMAIL, PASSWORD)))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("OAuth 연결 후 같은 provider ID로 로그인하면 기존 계정으로 토큰을 발급한다")
        void oauthLogin_afterLinkUsesExistingAccount() {
            AuthAccount account = signup();
            linkCodeRepository.save(
                    "link-code-login",
                    new com.pickbit.authservice.security.oauth.OAuthLinkContext(
                            account.getId(), OAuthProvider.KAKAO, "kakao-linked-login", EMAIL, "카카오닉네임"),
                    Duration.ofMinutes(10)
            );
            authCommandService.linkOAuthAccount(new OAuthLinkRequest("link-code-login", EMAIL, PASSWORD));

            OAuthLoginResult result = authCommandService.oauthLogin(
                    new OAuthUserInfo(OAuthProvider.KAKAO, "kakao-linked-login", EMAIL, "카카오닉네임")
            );

            assertThat(result.requiresSignup()).isFalse();
            assertThat(result.requiresLink()).isFalse();
            assertThat(result.tokenResponse().accessToken()).isNotBlank();
            assertThat(authAccountRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("OAuth 연결 시 기존 계정 비밀번호가 틀리면 실패한다")
        void linkOAuthAccount_wrongPassword() {
            AuthAccount account = signup();
            linkCodeRepository.save(
                    "link-code-wrong-password",
                    new com.pickbit.authservice.security.oauth.OAuthLinkContext(
                            account.getId(), OAuthProvider.KAKAO, "kakao-wrong-password", EMAIL, "카카오닉네임"),
                    Duration.ofMinutes(10)
            );

            assertThatThrownBy(() -> authCommandService.linkOAuthAccount(
                    new OAuthLinkRequest("link-code-wrong-password", EMAIL, "wrong-password")))
                    .isInstanceOf(InvalidCredentialException.class);
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
