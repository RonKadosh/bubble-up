package com.ronkadosh.studybuddy.common.datetime;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stub implementation for simulation. Can be set to a fixed time.
 * Not registered as primary — inject by name when needed.
 */
@Component("simulatedTimeProvider")
public class SimulatedTimeProvider implements TimeProvider {

    private final AtomicReference<Instant> fixedTime = new AtomicReference<>(Instant.now());

    @Override
    public Instant now() {
        return fixedTime.get();
    }

    public void setTime(Instant time) {
        fixedTime.set(time);
    }

    public void advance(java.time.Duration duration) {
        fixedTime.updateAndGet(t -> t.plus(duration));
    }
}
