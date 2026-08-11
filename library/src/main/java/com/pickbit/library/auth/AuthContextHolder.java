package com.pickbit.library.auth;

import com.pickbit.library.exception.ForbiddenException;

public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> CONTEXT = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthContext authContext) {
        CONTEXT.set(authContext);
    }

    public static AuthContext get() {
        AuthContext authContext = CONTEXT.get();
        if (authContext == null) {
            throw new IllegalStateException("인증 컨텍스트가 없습니다.");
        }
        return authContext;
    }

    public static Long getUserId() {
        return get().userId();
    }

    public static String getNickname() {
        return get().nickname();
    }

    public static String getRole() {
        return get().role();
    }

    /**
     * 호출자가 지정한 역할을 가지고 있는지 확인합니다.
     * 역할은 게이트웨이가 JWT에서 꺼내 {@code X-User-Role} 헤더로 전달합니다.
     *
     * @param role 요구되는 역할 (예: {@code "ADMIN"})
     * @throws ForbiddenException 역할이 일치하지 않는 경우
     */
    public static void requireRole(String role) {
        if (!role.equals(getRole())) {
            throw new ForbiddenException("이 작업을 수행할 권한이 없습니다.");
        }
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
