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
        Objects.requireNonNull(pixels, "pixels");
        int expectedPixels = checkedPixelCount(width, height);
        if (pixels.length != expectedPixels) {
            throw new IllegalArgumentException("Pixel array length does not match frame dimensions");
        }
        this.pixels = pixels.clone();
        this.width = width;
        this.height = height;
        this.durationMs = Math.max(durationMs, 50);
    }

    public AnimatedFrame(BufferedImage image, int durationMs) {
        Objects.requireNonNull(image, "image");
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.pixels = new int[checkedPixelCount(width, height)];
        image.getRGB(0, 0, width, height, this.pixels, 0, width);
        this.durationMs = Math.max(durationMs, 50);
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

    private static int checkedPixelCount(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive");
        }
        if (width > AnimatedImageLimits.DEFAULT.maxDimension || height > AnimatedImageLimits.DEFAULT.maxDimension) {
            throw new IllegalArgumentException("Frame dimensions exceed supported limits");
        }
        long pixels = (long) width * height;
        if (pixels > AnimatedImageLimits.DEFAULT.maxFramePixels) {
            throw new IllegalArgumentException("Frame pixel count exceeds supported limits");
        }
        return (int) pixels;
    }
}
