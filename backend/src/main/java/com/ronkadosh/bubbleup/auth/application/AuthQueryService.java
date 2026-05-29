package com.ronkadosh.bubbleup.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Placeholder to keep the auth module symmetric with the Command/Query convention used by
 * every other feature module. No public REST reads exist on the auth surface today — the
 * only auth reads are cross-module and live on {@code AuthInternalService}. If a per-user
 * read endpoint lands later (e.g. {@code GET /api/users?email=}), the method goes here.
 */
@Service
@RequiredArgsConstructor
public class AuthQueryService {
}
