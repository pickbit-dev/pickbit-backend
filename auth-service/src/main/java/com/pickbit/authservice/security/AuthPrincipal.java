package com.pickbit.authservice.security;

import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.domain.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 인증 주체입니다.
 *
 * @param accountId 인증 계정 ID
 * @param email 계정 이메일
 * @param nickname 사용자 닉네임
 * @param role 계정 역할
 * @param provider OAuth provider
 */
public record AuthPrincipal(
        Long accountId,
        String email,
        String nickname,
        Role role,
        OAuthProvider provider
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return String.valueOf(accountId);
    }
}
