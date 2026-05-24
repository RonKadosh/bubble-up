package com.ronkadosh.studybuddy.auth.internal;

import java.util.Optional;
import java.util.UUID;

public interface AuthInternalService {
    boolean userExists(UUID userId);
    Optional<String> getEmail(UUID userId);
}
