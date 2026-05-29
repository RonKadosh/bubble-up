package com.ronkadosh.bubbleup.common.context;

public interface CurrentUserProvider {
    CurrentUser get();
    boolean isAuthenticated();
}
