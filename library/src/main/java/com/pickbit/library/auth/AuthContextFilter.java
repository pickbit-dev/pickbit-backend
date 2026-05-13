package com.pickbit.library.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AuthContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            AuthContext authContext = resolveAuthContext(request);
            if (authContext != null) {
                AuthContextHolder.set(authContext);
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private AuthContext resolveAuthContext(HttpServletRequest request) {
        String userId = request.getHeader(AuthHeaders.USER_ID);
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return new AuthContext(
                Long.valueOf(userId),
                request.getHeader(AuthHeaders.USER_ROLE),
                request.getHeader(AuthHeaders.USER_NICKNAME),
                request.getHeader(AuthHeaders.USER_PROVIDER),
                request.getHeader(AuthHeaders.USER_EMAIL)
        );
    }
}
