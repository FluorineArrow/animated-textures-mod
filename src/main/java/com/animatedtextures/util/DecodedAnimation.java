package com.animatedtextures.util;

import java.util.List;
import java.util.Objects;

/**
 * Decoded animation frames with format-independent playback semantics.
 */
public record DecodedAnimation(List<AnimatedFrame> frames, long totalPlays) {

    public static final long INFINITE_PLAYS = -1;

    public DecodedAnimation {
        Objects.requireNonNull(frames, "frames");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("Decoded animation must contain at least one frame");
        }
        frames = List.copyOf(frames);
        if (totalPlays != INFINITE_PLAYS && totalPlays < 1) {
            throw new IllegalArgumentException("Total plays must be positive or INFINITE_PLAYS");
        }
    }

    public boolean playsInfinitely() {
        return totalPlays == INFINITE_PLAYS;
    }

    public long retainedPixels() {
        long pixels = 0;
        for (AnimatedFrame frame : frames) {
            pixels = Math.addExact(pixels, Math.multiplyExact((long) frame.getWidth(), frame.getHeight()));
        }
        return pixels;
    }
}
