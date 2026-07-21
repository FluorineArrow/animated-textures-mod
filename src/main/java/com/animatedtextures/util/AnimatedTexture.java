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

    private int currentFrameIndex;
    private long elapsedInFrameMs;
    private long completedPlays;
    private long frameRevision;
    private boolean finished;

    public AnimatedTexture(Identifier sourceId, List<AnimatedFrame> frames) {
        this(sourceId, new DecodedAnimation(frames, DecodedAnimation.INFINITE_PLAYS));
    }

    public AnimatedTexture(Identifier sourceId, DecodedAnimation animation) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(animation, "animation");
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
                durationMs = Math.addExact(durationMs, frame.getDurationMs());
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
        while (elapsedInFrameMs >= frames.get(currentFrameIndex).getDurationMs()) {
            elapsedInFrameMs -= frames.get(currentFrameIndex).getDurationMs();
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
        long durationMs = frames.get(0).getDurationMs();
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
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        AnimatedFrame frame = frames.get(currentFrameIndex);
        if (AnimatedTexturesConfig.get().shouldUseBilinear()
                && targetWidth >= frame.getWidth() && targetHeight >= frame.getHeight()) {
            return renderBilinear(frame, targetWidth, targetHeight);
        }
        return renderNearest(frame, targetWidth, targetHeight);
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

    private NativeImage renderNearest(AnimatedFrame frame, int targetWidth, int targetHeight) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, targetWidth, targetHeight, false);
        int[] pixels = frame.pixelsUnsafe();
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = (int) ((long) y * frame.getHeight() / targetHeight);
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = (int) ((long) x * frame.getWidth() / targetWidth);
                image.setColor(x, y, argbToAbgr(pixels[sourceY * frame.getWidth() + sourceX]));
            }
        }
        return image;
    }

    private NativeImage renderBilinear(AnimatedFrame frame, int targetWidth, int targetHeight) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, targetWidth, targetHeight, false);
        int[] pixels = resizeBilinearArgb(frame.pixelsUnsafe(), frame.getWidth(), frame.getHeight(),
                targetWidth, targetHeight);
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                image.setColor(x, y, argbToAbgr(pixels[y * targetWidth + x]));
            }
        }
        return image;
    }

    static int[] resizeBilinearArgb(int[] pixels, int sourceWidth, int sourceHeight,
                                    int targetWidth, int targetHeight) {
        int[] resized = new int[targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            float sourceY = ((y + 0.5f) * sourceHeight / targetHeight) - 0.5f;
            sourceY = Math.max(0, Math.min(sourceY, sourceHeight - 1));
            int y0 = (int) Math.floor(sourceY);
            int y1 = Math.min(y0 + 1, sourceHeight - 1);
            float yFraction = sourceY - y0;
            for (int x = 0; x < targetWidth; x++) {
                float sourceX = ((x + 0.5f) * sourceWidth / targetWidth) - 0.5f;
                sourceX = Math.max(0, Math.min(sourceX, sourceWidth - 1));
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
        int[] samples = {topLeft, topRight, bottomLeft, bottomRight};
        float[] weights = {topWeight, topRightWeight, bottomLeftWeight, bottomRightWeight};
        float alpha = 0;
        float red = 0;
        float green = 0;
        float blue = 0;
        for (int index = 0; index < samples.length; index++) {
            int sampleAlpha = samples[index] >>> 24;
            float weightedAlpha = sampleAlpha * weights[index];
            alpha += weightedAlpha;
            red += ((samples[index] >>> 16) & 0xFF) * weightedAlpha;
            green += ((samples[index] >>> 8) & 0xFF) * weightedAlpha;
            blue += (samples[index] & 0xFF) * weightedAlpha;
        }
        int outputAlpha = Math.round(alpha);
        if (outputAlpha == 0 || alpha == 0) {
            return 0;
        }
        int outputRed = clampChannel(Math.round(red / alpha));
        int outputGreen = clampChannel(Math.round(green / alpha));
        int outputBlue = clampChannel(Math.round(blue / alpha));
        return (clampChannel(outputAlpha) << 24) | (outputRed << 16) | (outputGreen << 8) | outputBlue;
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
    }
}
