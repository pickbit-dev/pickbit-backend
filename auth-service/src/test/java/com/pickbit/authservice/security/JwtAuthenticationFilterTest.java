package com.pickbit.authservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtTokenProvider.class));

    @Test
    @DisplayName("공개 인증 API는 JWT 필터를 건너뛴다")
    void shouldNotFilter_publicAuthPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/oauth/signup-context");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("보호 API는 JWT 필터를 적용한다")
    void shouldFilter_protectedPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
