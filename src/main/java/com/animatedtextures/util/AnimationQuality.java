package com.animatedtextures.util;

public enum AnimationQuality {
    STANDARD("Standard", false, false),
    HIGH_FRAME_RATE("High Frame Rate", true, false),
    HIGH_RESOLUTION("High Resolution", false, true),
    HIGH_QUALITY("High Quality", true, true);

    private static final long STANDARD_CACHE_BYTES = 64L * 1024 * 1024;
    private static final long HIGH_RESOLUTION_CACHE_BYTES = 192L * 1024 * 1024;

    private final String displayName;
    private final boolean renderFrameDriven;
    private final boolean highResolution;

    AnimationQuality(String displayName, boolean renderFrameDriven, boolean highResolution) {
        this.displayName = displayName;
        this.renderFrameDriven = renderFrameDriven;
        this.highResolution = highResolution;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AnimationQuality next() {
        AnimationQuality[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean isRenderFrameDriven() {
        return renderFrameDriven;
    }

    public boolean isHighResolution() {
        return highResolution;
    }

    public int minimumFrameDurationMs() {
        return renderFrameDriven ? 1 : 50;
    }

    public long preparedFrameCacheBytes() {
        return highResolution ? HIGH_RESOLUTION_CACHE_BYTES : STANDARD_CACHE_BYTES;
    }

    AnimatedImageLimits imageLimits() {
        return highResolution ? AnimatedImageLimits.HIGH_RESOLUTION : AnimatedImageLimits.DEFAULT;
    }

    AnimatedTextureReloadBudget newReloadBudget() {
        return highResolution
                ? new AnimatedTextureReloadBudget(
                AnimatedTextureReloadBudget.DEFAULT_MAX_ANIMATIONS,
                AnimatedTextureReloadBudget.DEFAULT_MAX_FRAMES,
                AnimatedTextureReloadBudget.HIGH_RESOLUTION_MAX_RETAINED_PIXELS,
                AnimatedTextureReloadBudget.HIGH_RESOLUTION_MAX_ESTIMATED_BYTES)
                : new AnimatedTextureReloadBudget();
    }
}
