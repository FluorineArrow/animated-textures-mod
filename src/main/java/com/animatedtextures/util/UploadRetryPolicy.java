package com.animatedtextures.util;

/**
 * Per-binding exponential retry schedule independent of changing animation revisions.
 */
final class UploadRetryPolicy {

    private final long initialDelay;
    private final long maximumDelay;
    private int failures;
    private long nextAttempt;

    UploadRetryPolicy(long maximumDelay) {
        this(1, maximumDelay);
    }

    UploadRetryPolicy(long initialDelay, long maximumDelay) {
        if (initialDelay < 1 || maximumDelay < initialDelay || maximumDelay >= Long.MAX_VALUE / 2) {
            throw new IllegalArgumentException("Maximum retry delay is out of range");
        }
        this.initialDelay = initialDelay;
        this.maximumDelay = maximumDelay;
    }

    boolean isDue(long now) {
        return failures == 0 || now - nextAttempt >= 0;
    }

    long recordFailure(long now) {
        failures = Math.min(failures + 1, 63);
        int shift = failures - 1;
        long delay = shift >= 63 || initialDelay > maximumDelay >> shift
                ? maximumDelay
                : Math.min(maximumDelay, initialDelay << shift);
        nextAttempt = now + delay;
        return delay;
    }

    int recordSuccess() {
        int recoveredFailures = failures;
        failures = 0;
        nextAttempt = 0;
        return recoveredFailures;
    }

    int failures() {
        return failures;
    }
}
