package com.pickbit.authservice.security;

import com.pickbit.authservice.security.oauth.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/authorization"))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/auth/oauth2/code/*"))
                        .successHandler(oauth2AuthenticationSuccessHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                // 액추에이터를 열어두지 않으면 oauth2Login 이 만든 로그인 페이지로
                                // 302 가 나가고, Prometheus 는 메트릭 대신 그 HTML 을 받아 스크레이프에
                                // 실패한다(그라파나에서 이 서비스만 DOWN 으로 보인다).
                                // "/actuator/**" 로 뭉뚱그리지 않는 이유는 게이트웨이와 같다 —
                                // 나중에 env 같은 엔드포인트를 노출했을 때 같이 열리면 안 된다.
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs.yaml",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
