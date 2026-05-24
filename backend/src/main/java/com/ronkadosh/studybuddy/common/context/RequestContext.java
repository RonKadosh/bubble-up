package com.ronkadosh.studybuddy.common.context;

import java.util.UUID;

public record RequestContext(
        CurrentUser currentUser,
        String traceId
) {
    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    public static void set(RequestContext context) {
        HOLDER.set(context);
    }

    public static RequestContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
