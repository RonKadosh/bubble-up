package com.ronkadosh.bubbleup.help.application;

import com.ronkadosh.bubbleup.common.context.UserRole;

public enum HelpAudience {
    STUDENT,
    EXPERT;

    public boolean visibleTo(UserRole role) {
        return switch (this) {
            case STUDENT -> true;
            case EXPERT -> role == UserRole.EXPERT || role == UserRole.ADMIN;
        };
    }
}
