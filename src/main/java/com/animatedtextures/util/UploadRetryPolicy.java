package com.animatedtextures.util;

/**
 * Per-binding exponential retry schedule independent of changing animation revisions.
 */
final class UploadRetryPolicy {

    private final long maximumDelayTicks;
    private int failures;
    private long nextAttemptTick;

    UploadRetryPolicy(long maximumDelayTicks) {
        if (maximumDelayTicks < 1 || maximumDelayTicks >= Long.MAX_VALUE / 2) {
            throw new IllegalArgumentException("Maximum retry delay is out of range");
        }
        this.maximumDelayTicks = maximumDelayTicks;
    }

    boolean isDue(long tick) {
        return failures == 0 || tick - nextAttemptTick >= 0;
    }

    long recordFailure(long tick) {
        failures = Math.min(failures + 1, 63);
        long delay = failures >= 63 ? maximumDelayTicks
                : Math.min(maximumDelayTicks, 1L << (failures - 1));
        nextAttemptTick = tick + delay;
        return delay;
    }

    int recordSuccess() {
        int recoveredFailures = failures;
        failures = 0;
        nextAttemptTick = 0;
        return recoveredFailures;
    }

    int failures() {
        return failures;
    }
}
