package com.animatedtextures.util;

final class AnimationFrameScheduler {

    static final long TARGET_INTERVAL_NANOS = 1_000_000_000L / 60;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private long lastPollNanos;
    private long throttleNanos;
    private long pendingElapsedNanos;
    private boolean initialized;

    private final long targetIntervalNanos;

    AnimationFrameScheduler() {
        this(TARGET_INTERVAL_NANOS);
    }

    AnimationFrameScheduler(long targetIntervalNanos) {
        if (targetIntervalNanos < 1) {
            throw new IllegalArgumentException("Target interval must be positive");
        }
        this.targetIntervalNanos = targetIntervalNanos;
    }

    long pollElapsedMillis(long nowNanos) {
        if (!initialized) {
            initialized = true;
            lastPollNanos = nowNanos;
            return 0;
        }
        long elapsedNanos = nowNanos - lastPollNanos;
        lastPollNanos = nowNanos;
        if (elapsedNanos <= 0) {
            return 0;
        }
        throttleNanos = saturatedAdd(throttleNanos, elapsedNanos);
        pendingElapsedNanos = saturatedAdd(pendingElapsedNanos, elapsedNanos);
        if (throttleNanos < targetIntervalNanos) {
            return 0;
        }
        throttleNanos %= targetIntervalNanos;
        long elapsedMillis = pendingElapsedNanos / NANOS_PER_MILLISECOND;
        pendingElapsedNanos %= NANOS_PER_MILLISECOND;
        return elapsedMillis;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
