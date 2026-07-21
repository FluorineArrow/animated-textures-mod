package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesClient;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Associates apply tasks and overall completion with one unique resource reload attempt.
 */
public final class AnimatedTextureReloadCoordinator {

    private static final ThreadLocal<AnimatedTextureReloadAttempt> CURRENT = new ThreadLocal<>();
    private static final AtomicReference<AnimatedTextureReloadAttempt> LATEST = new AtomicReference<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private AnimatedTextureReloadCoordinator() {
    }

    public static AnimatedTextureReloadAttempt begin() {
        AnimatedTextureReloadAttempt attempt = new AnimatedTextureReloadAttempt(SEQUENCE.incrementAndGet());
        AnimatedTextureReloadAttempt previous = LATEST.getAndSet(attempt);
        if (previous != null) {
            previous.abort(true);
        }
        return attempt;
    }

    public static Executor wrapApplyExecutor(AnimatedTextureReloadAttempt attempt, Executor delegate) {
        return command -> delegate.execute(() -> runWithAttempt(attempt, command));
    }

    public static AnimatedTextureReloadAttempt currentAttempt() {
        return CURRENT.get();
    }

    public static void complete(AnimatedTextureReloadAttempt attempt, Throwable failure) {
        if (failure != null || LATEST.get() != attempt) {
            attempt.abort(LATEST.get() != attempt);
            if (failure != null) {
                AnimatedTexturesClient.LOGGER.warn(
                        "[AnimatedTextures] repair category=reload action=aborted attempt={} reason={}",
                        attempt.sequence(), failure.getClass().getSimpleName());
            }
            return;
        }
        AnimatedTextureReloadAttempt.CommitData data = attempt.commitData();
        if (data == null || !LATEST.compareAndSet(attempt, null)) {
            attempt.abort(true);
            return;
        }
        AnimatedTextureTickManager.commit(data, attempt.sequence());
    }

    static void runWithAttempt(AnimatedTextureReloadAttempt attempt, Runnable command) {
        AnimatedTextureReloadAttempt previous = CURRENT.get();
        CURRENT.set(attempt);
        try {
            command.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
