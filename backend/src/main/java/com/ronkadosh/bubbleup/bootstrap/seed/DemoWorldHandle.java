package com.ronkadosh.bubbleup.bootstrap.seed;

import java.util.UUID;

/**
 * The handles {@code DemoWorldSeeder.seed(...)} returns so the demo module can
 * wire up the session: which University roots this world (for cleanup), who the
 * guest is (to issue a session for), and which Bubble the tour opens onto.
 */
public record DemoWorldHandle(
        UUID universityId,
        UUID guestUserId,
        UUID starterGroupId
) {}
