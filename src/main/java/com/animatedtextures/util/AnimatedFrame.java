package com.animatedtextures.util;

import java.awt.image.BufferedImage;

/**
 * Represents a single frame in an animated texture (GIF or APNG).
 */
public class AnimatedFrame {

    /** The pixel data for this frame (ARGB format). */
    private final int[] pixels;

    /** Width and height of this frame in pixels. */
    private final int width;
    private final int height;

    /**
     * Display duration of this frame in milliseconds.
     * GIF frames use centiseconds internally but we convert to ms here.
     */
    private final int durationMs;

    public AnimatedFrame(int[] pixels, int width, int height, int durationMs) {
        this.pixels = pixels.clone();
        this.width = width;
        this.height = height;
        this.durationMs = Math.max(durationMs, 50); // Enforce minimum 50ms (20fps cap)
    }

    public AnimatedFrame(BufferedImage image, int durationMs) {
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.durationMs = Math.max(durationMs, 50);
        this.pixels = new int[width * height];
        image.getRGB(0, 0, width, height, this.pixels, 0, width);
    }

    /**
     * Returns the frame's pixel data in ARGB format.
     * Safe to return directly because the internal array is already a clone
     * from construction, and the containing AnimatedTexture uses List.copyOf().
     */
    public int[] getPixels() {
        return pixels;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDurationMs() {
        return durationMs;
    }
}
