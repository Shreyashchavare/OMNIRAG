package com.omragul.knowledge.security;

public final class UserSecurityContextHolder {
    private static final ThreadLocal<UserSecurityContext> CONTEXT = new ThreadLocal<>();

    private UserSecurityContextHolder() {}

    public static void setContext(UserSecurityContext context) {
        CONTEXT.set(context);
    }

    public static UserSecurityContext getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
