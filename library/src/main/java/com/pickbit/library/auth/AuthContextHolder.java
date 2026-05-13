package com.pickbit.library.auth;

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

    public static void clear() {
        CONTEXT.remove();
    }
}
