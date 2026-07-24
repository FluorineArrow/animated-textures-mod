package com.animatedtextures.util;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Immutable ARGB frame data retained by an animated texture.
 */
public final class AnimatedFrame {

    private final int[] pixels;
    private final int width;
    private final int height;
    private final int durationMs;

    public AnimatedFrame(int[] pixels, int width, int height, int durationMs) {
        this(pixels, width, height, durationMs, AnimatedImageLimits.DEFAULT);
    }

    AnimatedFrame(int[] pixels, int width, int height, int durationMs, AnimatedImageLimits limits) {
        Objects.requireNonNull(pixels, "pixels");
        int expectedPixels = checkedPixelCount(width, height, limits);
        if (pixels.length != expectedPixels) {
            throw new IllegalArgumentException("Pixel array length does not match frame dimensions");
        }
        this.pixels = pixels.clone();
        this.width = width;
        this.height = height;
        this.durationMs = Math.max(durationMs, 1);
    }

    public AnimatedFrame(BufferedImage image, int durationMs) {
        this(image, durationMs, AnimatedImageLimits.DEFAULT);
    }

    AnimatedFrame(BufferedImage image, int durationMs, AnimatedImageLimits limits) {
        Objects.requireNonNull(image, "image");
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.pixels = new int[checkedPixelCount(width, height, limits)];
        image.getRGB(0, 0, width, height, this.pixels, 0, width);
        this.durationMs = Math.max(durationMs, 1);
    }

    /**
     * Returns a defensive copy of the frame's ARGB pixels.
     */
    public int[] getPixels() {
        return pixels.clone();
    }

    int[] pixelsUnsafe() {
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

    private static int checkedPixelCount(int width, int height, AnimatedImageLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive");
        }
        if (width > limits.maxDimension || height > limits.maxDimension) {
            throw new IllegalArgumentException("Frame dimensions exceed supported limits");
        }
        long pixels = (long) width * height;
        if (pixels > limits.maxFramePixels) {
            throw new IllegalArgumentException("Frame pixel count exceeds supported limits");
        }
        return (int) pixels;
    }
}
