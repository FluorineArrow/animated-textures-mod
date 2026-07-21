package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimatedTextureReloadCoordinatorTest {

    @Test
    void executorContextIsScopedAndRestored() {
        AnimatedTextureReloadAttempt attempt = AnimatedTextureReloadCoordinator.begin();
        AtomicReference<AnimatedTextureReloadAttempt> observed = new AtomicReference<>();

        AnimatedTextureReloadCoordinator.wrapApplyExecutor(attempt, Runnable::run)
                .execute(() -> observed.set(AnimatedTextureReloadCoordinator.currentAttempt()));

        assertEquals(attempt, observed.get());
        assertNull(AnimatedTextureReloadCoordinator.currentAttempt());
        AnimatedTextureReloadCoordinator.complete(attempt, new RuntimeException("test abort"));
    }

    @Test
    void olderAttemptCannotCommitAfterNewerAttemptStarts() {
        AnimatedTextureReloadAttempt old = AnimatedTextureReloadCoordinator.begin();
        AnimatedTextureReloadAttempt newer = AnimatedTextureReloadCoordinator.begin();

        AnimatedTextureReloadCoordinator.complete(old, null);

        assertNull(old.commitData());
        AnimatedTextureReloadCoordinator.complete(newer, new RuntimeException("test abort"));
    }
}
