package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnimationQualityTest {

    @Test
    void cyclesThroughAllFourModes() {
        assertEquals(AnimationQuality.HIGH_FRAME_RATE, AnimationQuality.STANDARD.next());
        assertEquals(AnimationQuality.HIGH_RESOLUTION, AnimationQuality.HIGH_FRAME_RATE.next());
        assertEquals(AnimationQuality.HIGH_QUALITY, AnimationQuality.HIGH_RESOLUTION.next());
        assertEquals(AnimationQuality.STANDARD, AnimationQuality.HIGH_QUALITY.next());
    }

    @Test
    void highResolutionHasAnExact4096PixelBoundary() throws IOException {
        assertEquals(16_777_216,
                AnimationQuality.HIGH_RESOLUTION.imageLimits().checkedPixels(4_096, 4_096, "test"));
        assertThrows(IOException.class,
                () -> AnimationQuality.HIGH_RESOLUTION.imageLimits().checkedPixels(4_097, 1, "test"));
        assertThrows(IOException.class,
                () -> AnimationQuality.STANDARD.imageLimits().checkedPixels(4_096, 4_096, "test"));
    }
}
