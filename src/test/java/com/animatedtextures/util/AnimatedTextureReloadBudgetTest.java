package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatedTextureReloadBudgetTest {

    @Test
    void exactLimitsAreAcceptedAndOneOverIsRejectedAtomically() {
        AnimatedTextureReloadBudget budget = new AnimatedTextureReloadBudget(1, 2, 2, 8);
        DecodedAnimation accepted = animation(2);

        assertTrue(budget.tryReserve(accepted));
        assertFalse(budget.tryReserve(animation(1)));
        assertEquals(1, budget.animations());
        assertEquals(2, budget.frames());
        assertEquals(2, budget.retainedPixels());
        assertEquals(8, budget.estimatedBytes());
    }

    @Test
    void failedMultiDimensionReservationConsumesNothing() {
        AnimatedTextureReloadBudget budget = new AnimatedTextureReloadBudget(2, 1, 10, 40);

        assertFalse(budget.tryReserve(animation(2)));
        assertEquals(0, budget.animations());
        assertEquals(0, budget.frames());
        assertEquals(0, budget.retainedPixels());
    }

    @Test
    void qualityCreatesTheMatchingReloadBudget() {
        assertEquals(AnimatedTextureReloadBudget.DEFAULT_MAX_RETAINED_PIXELS,
                AnimationQuality.STANDARD.newReloadBudget().remaining().retainedPixels());
        assertEquals(AnimatedTextureReloadBudget.HIGH_RESOLUTION_MAX_RETAINED_PIXELS,
                AnimationQuality.HIGH_RESOLUTION.newReloadBudget().remaining().retainedPixels());
    }

    private static DecodedAnimation animation(int frames) {
        java.util.ArrayList<AnimatedFrame> result = new java.util.ArrayList<>();
        for (int index = 0; index < frames; index++) {
            result.add(new AnimatedFrame(new int[1], 1, 1, 50));
        }
        return new DecodedAnimation(List.copyOf(result), 1);
    }
}
