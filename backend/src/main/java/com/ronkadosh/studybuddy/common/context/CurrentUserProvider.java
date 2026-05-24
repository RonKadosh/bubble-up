package com.ronkadosh.studybuddy.common.context;

public interface CurrentUserProvider {
    CurrentUser get();
    boolean isAuthenticated();
}
