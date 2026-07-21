package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnimatedFrameTest {

    @Test
    void ownsItsPixelData() {
        int[] input = {0xFF112233};
        AnimatedFrame frame = new AnimatedFrame(input, 1, 1, 10);

        input[0] = 0;
        int[] exposed = frame.getPixels();
        exposed[0] = 0;

        assertArrayEquals(new int[]{0xFF112233}, frame.getPixels());
        assertEquals(50, frame.getDurationMs());
    }

    @Test
    void rejectsInconsistentPixelDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new AnimatedFrame(new int[1], 2, 2, 50));
        assertThrows(IllegalArgumentException.class, () -> new AnimatedFrame(new int[0], 0, 1, 50));
    }
}
