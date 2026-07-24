package com.animatedtextures.util;

/**
 * Fixed reload-wide limits and atomic accounting for retained animation data.
 */
public final class AnimatedTextureReloadBudget {

    public static final int DEFAULT_MAX_ANIMATIONS = 256;
    public static final int DEFAULT_MAX_FRAMES = 1_024;
    public static final long DEFAULT_MAX_RETAINED_PIXELS = 16_777_216L;
    public static final long DEFAULT_MAX_ESTIMATED_BYTES = DEFAULT_MAX_RETAINED_PIXELS * Integer.BYTES;
    public static final long HIGH_RESOLUTION_MAX_RETAINED_PIXELS = 33_554_432L;
    public static final long HIGH_RESOLUTION_MAX_ESTIMATED_BYTES =
            HIGH_RESOLUTION_MAX_RETAINED_PIXELS * Integer.BYTES;

    private final int maxAnimations;
    private final int maxFrames;
    private final long maxRetainedPixels;
    private final long maxEstimatedBytes;

    private int animations;
    private int frames;
    private long retainedPixels;
    private long estimatedBytes;

    public AnimatedTextureReloadBudget() {
        this(DEFAULT_MAX_ANIMATIONS, DEFAULT_MAX_FRAMES,
                DEFAULT_MAX_RETAINED_PIXELS, DEFAULT_MAX_ESTIMATED_BYTES);
    }

    AnimatedTextureReloadBudget(int maxAnimations, int maxFrames,
                                long maxRetainedPixels, long maxEstimatedBytes) {
        if (maxAnimations < 0 || maxFrames < 0 || maxRetainedPixels < 0 || maxEstimatedBytes < 0) {
            throw new IllegalArgumentException("Reload budget limits must not be negative");
        }
        this.maxAnimations = maxAnimations;
        this.maxFrames = maxFrames;
        this.maxRetainedPixels = maxRetainedPixels;
        this.maxEstimatedBytes = maxEstimatedBytes;
    }

    public record Remaining(int animationSlots, int frames, long retainedPixels, long estimatedBytes) {
        public boolean canDecode() {
            return animationSlots > 0 && frames > 0 && retainedPixels > 0 && estimatedBytes >= Integer.BYTES;
        }

        public long effectivePixels() {
            return Math.min(retainedPixels, estimatedBytes / Integer.BYTES);
        }
    }

    public Remaining remaining() {
        return new Remaining(maxAnimations - animations, maxFrames - frames,
                maxRetainedPixels - retainedPixels, maxEstimatedBytes - estimatedBytes);
    }

    boolean tryReserve(DecodedAnimation animation) {
        int nextAnimations;
        int nextFrames;
        long nextPixels;
        long nextBytes;
        try {
            nextAnimations = Math.addExact(animations, 1);
            nextFrames = Math.addExact(frames, animation.frames().size());
            nextPixels = Math.addExact(retainedPixels, animation.retainedPixels());
            nextBytes = Math.multiplyExact(nextPixels, Integer.BYTES);
        } catch (ArithmeticException exception) {
            return false;
        }
        if (nextAnimations > maxAnimations || nextFrames > maxFrames
                || nextPixels > maxRetainedPixels || nextBytes > maxEstimatedBytes) {
            return false;
        }
        animations = nextAnimations;
        frames = nextFrames;
        retainedPixels = nextPixels;
        estimatedBytes = nextBytes;
        return true;
    }

    int animations() {
        return animations;
    }

    int frames() {
        return frames;
    }

    long retainedPixels() {
        return retainedPixels;
    }

    long estimatedBytes() {
        return estimatedBytes;
    }
}
