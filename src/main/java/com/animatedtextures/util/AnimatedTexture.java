package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesConfig;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Holds all frames for one animated texture and manages its displayed frame.
 */
public final class AnimatedTexture {

    private final Identifier sourceId;
    private final List<AnimatedFrame> frames;
    private final int totalWidth;
    private final int totalHeight;
    private final Identifier targetTextureId;
    private final long cycleDurationMs;
    private final long totalPlays;
    private final AnimationQuality quality;

    private int currentFrameIndex;
    private long elapsedInFrameMs;
    private long completedPlays;
    private long frameRevision;
    private boolean finished;

    public AnimatedTexture(Identifier sourceId, List<AnimatedFrame> frames) {
        this(sourceId, new DecodedAnimation(frames, DecodedAnimation.INFINITE_PLAYS), AnimationQuality.STANDARD);
    }

    public AnimatedTexture(Identifier sourceId, DecodedAnimation animation) {
        this(sourceId, animation, AnimationQuality.STANDARD);
    }

    public AnimatedTexture(Identifier sourceId, DecodedAnimation animation, AnimationQuality quality) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(animation, "animation");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.frames = animation.frames();
        this.totalPlays = animation.totalPlays();
        this.totalWidth = frames.get(0).getWidth();
        this.totalHeight = frames.get(0).getHeight();
        long durationMs = 0;
        for (AnimatedFrame frame : this.frames) {
            if (frame.getWidth() != totalWidth || frame.getHeight() != totalHeight) {
                throw new IllegalArgumentException("AnimatedTexture frames must have identical dimensions: " + sourceId);
            }
            try {
                durationMs = Math.addExact(durationMs, effectiveDurationMs(frame));
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("AnimatedTexture cycle duration is too large: " + sourceId, exception);
            }
        }
        this.cycleDurationMs = durationMs;
        this.targetTextureId = toTextureIdentifier(sourceId);
    }

    /**
     * Advances the animation by the supplied elapsed time.
     *
     * @return true if at least one frame transition occurred
     */
    public boolean tick(long elapsedMs) {
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("Elapsed time must not be negative");
        }
        if (elapsedMs == 0 || finished) {
            return false;
        }
        if (frames.size() == 1) {
            return tickSingleFrame(elapsedMs);
        }
        long wholeCycles = elapsedMs / cycleDurationMs;
        long elapsedRemainder = elapsedMs % cycleDurationMs;
        long combinedRemainder = elapsedInFrameMs + elapsedRemainder;
        if (combinedRemainder >= cycleDurationMs) {
            combinedRemainder -= cycleDurationMs;
            wholeCycles++;
        }
        elapsedInFrameMs = combinedRemainder;
        boolean changed = false;
        long cyclesToSkip = wholeCycles;
        if (cyclesToSkip > 0) {
            if (totalPlays == DecodedAnimation.INFINITE_PLAYS) {
                frameRevision += cyclesToSkip * frames.size();
                completedPlays += cyclesToSkip;
                changed = true;
            } else {
                long nonterminalCycles = Math.max(0, totalPlays - completedPlays - 1);
                long skippedCycles = Math.min(cyclesToSkip, nonterminalCycles);
                if (skippedCycles > 0) {
                    completedPlays += skippedCycles;
                    frameRevision += skippedCycles * frames.size();
                    changed = true;
                }
                if (cyclesToSkip > skippedCycles) {
                    elapsedInFrameMs += cycleDurationMs;
                }
            }
        }
        while (elapsedInFrameMs >= effectiveDurationMs(frames.get(currentFrameIndex))) {
            elapsedInFrameMs -= effectiveDurationMs(frames.get(currentFrameIndex));
            if (currentFrameIndex == frames.size() - 1) {
                if (totalPlays != DecodedAnimation.INFINITE_PLAYS && completedPlays + 1 >= totalPlays) {
                    completedPlays = totalPlays;
                    elapsedInFrameMs = 0;
                    finished = true;
                    break;
                }
                completedPlays++;
                currentFrameIndex = 0;
            } else {
                currentFrameIndex++;
            }
            frameRevision++;
            changed = true;
        }
        return changed;
    }

    private boolean tickSingleFrame(long elapsedMs) {
        if (totalPlays == DecodedAnimation.INFINITE_PLAYS) {
            return false;
        }
        long durationMs = effectiveDurationMs(frames.get(0));
        long playsElapsed = elapsedMs / durationMs;
        long remainder = elapsedMs % durationMs;
        long combinedRemainder = elapsedInFrameMs + remainder;
        if (combinedRemainder >= durationMs) {
            combinedRemainder -= durationMs;
            playsElapsed++;
        }
        elapsedInFrameMs = combinedRemainder;
        long playsRemaining = totalPlays - completedPlays;
        if (playsElapsed >= playsRemaining) {
            completedPlays = totalPlays;
            elapsedInFrameMs = 0;
            finished = true;
        } else {
            completedPlays += playsElapsed;
        }
        return false;
    }

    public NativeImage getCurrentFrameResized(int targetWidth, int targetHeight) {
        return getCurrentFrameResized(targetWidth, targetHeight, AnimatedTexturesConfig.get().scalingMode);
    }

    public NativeImage getCurrentFrameResized(int targetWidth, int targetHeight,
                                              AnimatedTexturesConfig.ScalingMode scalingMode) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, targetWidth, targetHeight, false);
        renderCurrentFrame(image, scalingMode);
        return image;
    }

    public void renderCurrentFrame(NativeImage image, AnimatedTexturesConfig.ScalingMode scalingMode) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(scalingMode, "scalingMode");
        AnimatedFrame frame = frames.get(currentFrameIndex);
        if (scalingMode == AnimatedTexturesConfig.ScalingMode.BILINEAR
                && image.getWidth() >= frame.getWidth() && image.getHeight() >= frame.getHeight()) {
            renderBilinear(frame, image);
        } else {
            renderNearest(frame, image);
        }
    }

    public Identifier getSourceId() {
        return sourceId;
    }

    public Identifier getTargetTextureId() {
        return targetTextureId;
    }

    /**
     * Returns ordered sprite IDs valid for the given vanilla atlas.
     */
    public List<Identifier> getSpriteIdCandidates(Identifier atlasId) {
        List<Identifier> candidates = new ArrayList<>();
        candidates.add(targetTextureId);
        String targetPath = targetTextureId.getPath();
        if ("textures/atlas/mob_effects.png".equals(atlasId.getPath()) && targetPath.startsWith("mob_effect/")) {
            candidates.add(Identifier.of(sourceId.getNamespace(), targetPath.substring("mob_effect/".length())));
        }
        if ("textures/atlas/gui.png".equals(atlasId.getPath()) && targetPath.startsWith("gui/sprites/")) {
            candidates.add(Identifier.of(sourceId.getNamespace(), targetPath.substring("gui/sprites/".length())));
        }
        return List.copyOf(candidates);
    }

    public int getFrameCount() {
        return frames.size();
    }

    List<AnimatedFrame> frames() {
        return frames;
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public long getFrameRevision() {
        return frameRevision;
    }

    public boolean isFinished() {
        return finished;
    }

    public long getTotalPlays() {
        return totalPlays;
    }

    public AnimationQuality getQuality() {
        return quality;
    }

    public int getTotalWidth() {
        return totalWidth;
    }

    public int getTotalHeight() {
        return totalHeight;
    }

    private static Identifier toTextureIdentifier(Identifier sourceId) {
        String path = sourceId.getPath();
        if (!path.startsWith("textures/")) {
            throw new IllegalArgumentException("Animated texture must be under textures/: " + sourceId);
        }
        String withoutPrefix = path.substring("textures/".length());
        if (withoutPrefix.endsWith(".gif")) {
            withoutPrefix = withoutPrefix.substring(0, withoutPrefix.length() - 4);
        } else if (withoutPrefix.endsWith(".png3")) {
            withoutPrefix = withoutPrefix.substring(0, withoutPrefix.length() - 5);
        } else {
            throw new IllegalArgumentException("Animated texture must use .gif or .png3: " + sourceId);
        }
        return Identifier.of(sourceId.getNamespace(), withoutPrefix);
    }

    private int effectiveDurationMs(AnimatedFrame frame) {
        return Math.max(frame.getDurationMs(), quality.minimumFrameDurationMs());
    }

    private void renderNearest(AnimatedFrame frame, NativeImage image) {
        int targetWidth = image.getWidth();
        int targetHeight = image.getHeight();
        int[] pixels = frame.pixelsUnsafe();
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = (int) ((long) y * frame.getHeight() / targetHeight);
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = (int) ((long) x * frame.getWidth() / targetWidth);
                image.setColor(x, y, argbToAbgr(pixels[sourceY * frame.getWidth() + sourceX]));
            }
        }
    }

    private void renderBilinear(AnimatedFrame frame, NativeImage image) {
        int targetWidth = image.getWidth();
        int targetHeight = image.getHeight();
        int[] pixels = frame.pixelsUnsafe();
        int sourceWidth = frame.getWidth();
        int sourceHeight = frame.getHeight();
        for (int y = 0; y < targetHeight; y++) {
            float sourceY = sourceCoordinate(y, sourceHeight, targetHeight);
            int y0 = (int) Math.floor(sourceY);
            int y1 = Math.min(y0 + 1, sourceHeight - 1);
            float yFraction = sourceY - y0;
            for (int x = 0; x < targetWidth; x++) {
                float sourceX = sourceCoordinate(x, sourceWidth, targetWidth);
                int x0 = (int) Math.floor(sourceX);
                int x1 = Math.min(x0 + 1, sourceWidth - 1);
                float xFraction = sourceX - x0;
                int argb = interpolateArgb(
                        pixels[y0 * sourceWidth + x0], pixels[y0 * sourceWidth + x1],
                        pixels[y1 * sourceWidth + x0], pixels[y1 * sourceWidth + x1],
                        xFraction, yFraction);
                image.setColor(x, y, argbToAbgr(argb));
            }
        }
    }

    static int[] resizeBilinearArgb(int[] pixels, int sourceWidth, int sourceHeight,
                                    int targetWidth, int targetHeight) {
        int[] resized = new int[targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            float sourceY = sourceCoordinate(y, sourceHeight, targetHeight);
            int y0 = (int) Math.floor(sourceY);
            int y1 = Math.min(y0 + 1, sourceHeight - 1);
            float yFraction = sourceY - y0;
            for (int x = 0; x < targetWidth; x++) {
                float sourceX = sourceCoordinate(x, sourceWidth, targetWidth);
                int x0 = (int) Math.floor(sourceX);
                int x1 = Math.min(x0 + 1, sourceWidth - 1);
                float xFraction = sourceX - x0;
                resized[y * targetWidth + x] = interpolateArgb(
                        pixels[y0 * sourceWidth + x0], pixels[y0 * sourceWidth + x1],
                        pixels[y1 * sourceWidth + x0], pixels[y1 * sourceWidth + x1],
                        xFraction, yFraction);
            }
        }
        return resized;
    }

    private static int interpolateArgb(int topLeft, int topRight, int bottomLeft, int bottomRight,
                                       float xFraction, float yFraction) {
        float topWeight = (1 - xFraction) * (1 - yFraction);
        float topRightWeight = xFraction * (1 - yFraction);
        float bottomLeftWeight = (1 - xFraction) * yFraction;
        float bottomRightWeight = xFraction * yFraction;
        float topLeftAlpha = (topLeft >>> 24) * topWeight;
        float topRightAlpha = (topRight >>> 24) * topRightWeight;
        float bottomLeftAlpha = (bottomLeft >>> 24) * bottomLeftWeight;
        float bottomRightAlpha = (bottomRight >>> 24) * bottomRightWeight;
        float alpha = topLeftAlpha + topRightAlpha + bottomLeftAlpha + bottomRightAlpha;
        int outputAlpha = Math.round(alpha);
        if (outputAlpha == 0 || alpha == 0) {
            return 0;
        }
        float red = ((topLeft >>> 16) & 0xFF) * topLeftAlpha
                + ((topRight >>> 16) & 0xFF) * topRightAlpha
                + ((bottomLeft >>> 16) & 0xFF) * bottomLeftAlpha
                + ((bottomRight >>> 16) & 0xFF) * bottomRightAlpha;
        float green = ((topLeft >>> 8) & 0xFF) * topLeftAlpha
                + ((topRight >>> 8) & 0xFF) * topRightAlpha
                + ((bottomLeft >>> 8) & 0xFF) * bottomLeftAlpha
                + ((bottomRight >>> 8) & 0xFF) * bottomRightAlpha;
        float blue = (topLeft & 0xFF) * topLeftAlpha
                + (topRight & 0xFF) * topRightAlpha
                + (bottomLeft & 0xFF) * bottomLeftAlpha
                + (bottomRight & 0xFF) * bottomRightAlpha;
        int outputRed = clampChannel(Math.round(red / alpha));
        int outputGreen = clampChannel(Math.round(green / alpha));
        int outputBlue = clampChannel(Math.round(blue / alpha));
        return (clampChannel(outputAlpha) << 24) | (outputRed << 16) | (outputGreen << 8) | outputBlue;
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float sourceCoordinate(int targetCoordinate, int sourceSize, int targetSize) {
        float source = ((targetCoordinate + 0.5f) * sourceSize / targetSize) - 0.5f;
        return Math.max(0, Math.min(source, sourceSize - 1));
    }

    private static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
    }
}
