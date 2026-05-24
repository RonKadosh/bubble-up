package com.ronkadosh.studybuddy.common.datetime;

import java.time.Instant;

public interface TimeProvider {
    Instant now();
}
