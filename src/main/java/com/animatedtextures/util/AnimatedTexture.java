package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesConfig;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Holds all frames for a single animated texture and manages the current
 * frame pointer. Updated every client tick via the tick manager.
 */
public class AnimatedTexture {

    private final Identifier sourceId;
    private final List<AnimatedFrame> frames;
    private final int totalWidth;
    private final int totalHeight;

    private volatile int currentFrameIndex = 0;
    private volatile long lastFrameTime = -1; // -1 = not yet initialized; set on first tick

    public AnimatedTexture(Identifier sourceId, List<AnimatedFrame> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("AnimatedTexture must have at least one frame: " + sourceId);
        }
        this.sourceId = sourceId;
        this.frames = List.copyOf(frames);
        // Normalize: all frames are expected to be the same dimensions
        this.totalWidth = frames.get(0).getWidth();
        this.totalHeight = frames.get(0).getHeight();
    }

    /**
     * Called each game tick (~every 50ms). Advances the frame if the current
     * frame's duration has elapsed.
     */
    public void tick(long currentTimeMs) {
        if (frames.size() <= 1) return;

        // Initialize lastFrameTime on first tick so frame 0 displays for its full duration
        if (lastFrameTime < 0) {
            lastFrameTime = currentTimeMs;
            return;
        }

        AnimatedFrame current = frames.get(currentFrameIndex);
        if (currentTimeMs - lastFrameTime >= current.getDurationMs()) {
            currentFrameIndex = (currentFrameIndex + 1) % frames.size();
            lastFrameTime = currentTimeMs;
        }
    }

    /**
     * Builds a NativeImage containing the current frame's pixels,
     * suitable for uploading to the GPU texture.
     */
    public NativeImage getCurrentFrameAsNativeImage() {
        AnimatedFrame frame = frames.get(currentFrameIndex);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, frame.getWidth(), frame.getHeight(), false);
        int[] pixels = frame.getPixels();
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int argb = pixels[y * frame.getWidth() + x];
                // Convert ARGB -> RGBA (Minecraft's NativeImage expects RGBA, little-endian ABGR on GPU)
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                // NativeImage.setColor expects ABGR packed
                // NativeImage.setColor expects ABGR packed int
                image.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return image;
    }

    /**
     * Returns a resized copy of the current frame as a NativeImage.
     * Uses nearest-neighbor sampling to scale to the target dimensions.
     * This allows animated textures at any resolution (16×16, 32×32, 64×64, etc.)
     * to match the sprite's allocated region in the atlas.
     *
     * @param targetW target width (sprite width in atlas)
     * @param targetH target height (sprite height in atlas)
     */
    public NativeImage getCurrentFrameAsNativeImage(int targetW, int targetH) {
        AnimatedFrame frame = frames.get(currentFrameIndex);
        int srcW = frame.getWidth();
        int srcH = frame.getHeight();

        // Fast path: dimensions match, no resize needed
        if (srcW == targetW && srcH == targetH) {
            return getCurrentFrameAsNativeImage();
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, targetW, targetH, false);
        int[] pixels = frame.getPixels();

        for (int y = 0; y < targetH; y++) {
            int srcY = (int) ((long) y * srcH / targetH);
            for (int x = 0; x < targetW; x++) {
                int srcX = (int) ((long) x * srcW / targetW);
                int argb = pixels[srcY * srcW + srcX];
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                image.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return image;
    }

    /**
     * Returns a resized copy of the current frame using bilinear interpolation.
     * Produces smoother results than nearest-neighbor when upscaling, which is
     * ideal for high-resolution resource packs.
     *
     * @param targetW target width (sprite width in atlas)
     * @param targetH target height (sprite height in atlas)
     */
    public NativeImage getCurrentFrameAsNativeImageBilinear(int targetW, int targetH) {
        AnimatedFrame frame = frames.get(currentFrameIndex);
        int srcW = frame.getWidth();
        int srcH = frame.getHeight();

        // Fast path: dimensions match, no resize needed
        if (srcW == targetW && srcH == targetH) {
            return getCurrentFrameAsNativeImage();
        }

        // For downscaling, fall back to nearest-neighbor (faster, no aliasing issues)
        if (targetW < srcW || targetH < srcH) {
            return getCurrentFrameAsNativeImage(targetW, targetH);
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, targetW, targetH, false);
        int[] pixels = frame.getPixels();

        for (int y = 0; y < targetH; y++) {
            // Calculate source Y with floating point precision
            float srcYf = (float) y * srcH / targetH;
            int y0 = (int) Math.floor(srcYf);
            int y1 = Math.min(y0 + 1, srcH - 1);
            float fy = srcYf - y0;

            for (int x = 0; x < targetW; x++) {
                float srcXf = (float) x * srcW / targetW;
                int x0 = (int) Math.floor(srcXf);
                int x1 = Math.min(x0 + 1, srcW - 1);
                float fx = srcXf - x0;

                // Get 4 neighboring pixels (ARGB format)
                int p00 = pixels[y0 * srcW + x0];
                int p10 = pixels[y0 * srcW + x1];
                int p01 = pixels[y1 * srcW + x0];
                int p11 = pixels[y1 * srcW + x1];

                // Bilinear interpolation for each channel
                int a = bilinearChannel((p00 >> 24) & 0xFF, (p10 >> 24) & 0xFF,
                                       (p01 >> 24) & 0xFF, (p11 >> 24) & 0xFF, fx, fy);
                int r = bilinearChannel((p00 >> 16) & 0xFF, (p10 >> 16) & 0xFF,
                                       (p01 >> 16) & 0xFF, (p11 >> 16) & 0xFF, fx, fy);
                int g = bilinearChannel((p00 >> 8) & 0xFF, (p10 >> 8) & 0xFF,
                                       (p01 >> 8) & 0xFF, (p11 >> 8) & 0xFF, fx, fy);
                int b = bilinearChannel(p00 & 0xFF, p10 & 0xFF,
                                       p01 & 0xFF, p11 & 0xFF, fx, fy);

                // Pack ABGR for NativeImage
                image.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return image;
    }

    /**
     * Performs bilinear interpolation on a single color channel.
     */
    private static int bilinearChannel(int c00, int c10, int c01, int c11, float fx, float fy) {
        float top = c00 * (1 - fx) + c10 * fx;
        float bottom = c01 * (1 - fx) + c11 * fx;
        return Math.round(top * (1 - fy) + bottom * fy);
    }

    /**
     * Returns a resized copy of the current frame using the configured scaling mode.
     * This is the main entry point for frame scaling.
     *
     * @param targetW target width (sprite width in atlas)
     * @param targetH target height (sprite height in atlas)
     */
    public NativeImage getCurrentFrameResized(int targetW, int targetH) {
        if (AnimatedTexturesConfig.get().shouldUseBilinear()) {
            return getCurrentFrameAsNativeImageBilinear(targetW, targetH);
        }
        return getCurrentFrameAsNativeImage(targetW, targetH);
    }

    public Identifier getSourceId() {
        return sourceId;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public int getTotalWidth() {
        return totalWidth;
    }

    public int getTotalHeight() {
        return totalHeight;
    }

    /**
     * Returns the identifier matching what SpriteContents.getId() returns.
     * SpriteContents id format: "minecraft:block/gold_ore"  (no textures/, no .png)
     *
     * ResourceManager gives us:  "minecraft:textures/block/gold_ore.gif"
     * We convert to:             "minecraft:block/gold_ore"
     *
     * Special case: mob_effect textures use a dedicated atlas with prefix="",
     * so sprites get bare registry names (e.g. "minecraft:fire_resistance").
     * We register both IDs so the sprite can be found in either atlas format.
     */
    public Identifier getTargetTextureId() {
        String path = sourceId.getPath(); // e.g. "textures/block/gold_ore.gif"

        // Strip "textures/" prefix
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length()); // "block/gold_ore.gif"
        }

        // Strip the animated extension entirely (SpriteContents id has no extension)
        if (path.endsWith(".gif")) {
            path = path.substring(0, path.length() - 4);       // "block/gold_ore"
        } else if (path.endsWith(".png3")) {
            path = path.substring(0, path.length() - 5);       // "block/gold_ore"
        } else if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);       // "block/gold_ore"
        }

        return Identifier.of(sourceId.getNamespace(), path); // "minecraft:block/gold_ore"
    }

    /**
     * Returns the bare registry-name identifier for mob_effect textures.
     * The mob_effects atlas uses prefix="" so sprite IDs are just the effect name
     * (e.g. "minecraft:fire_resistance" instead of "minecraft:mob_effect/fire_resistance").
     *
     * @return the bare name identifier, or null if this is not a mob_effect texture
     */
    public Identifier getMobEffectTargetId() {
        String path = sourceId.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (!path.startsWith("mob_effect/")) return null;

        // Strip mob_effect/ prefix
        path = path.substring("mob_effect/".length());

        // Strip extension
        if (path.endsWith(".gif")) {
            path = path.substring(0, path.length() - 4);
        } else if (path.endsWith(".png3")) {
            path = path.substring(0, path.length() - 5);
        } else if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }

        return Identifier.of(sourceId.getNamespace(), path); // "minecraft:fire_resistance"
    }
}
