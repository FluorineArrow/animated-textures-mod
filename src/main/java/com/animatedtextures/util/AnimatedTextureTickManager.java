package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.texture.MipmapHelper;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks exact stitched sprite bindings and uploads changed animation frames.
 */
public final class AnimatedTextureTickManager {

    private static final long CLIENT_TICK_MS = 50;
    private static final long MAXIMUM_RETRY_DELAY_TICKS = 100;
    private static final AtomicBoolean TICK_REGISTERED = new AtomicBoolean();
    private static final AtomicReference<ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite>> ACTIVE =
            new AtomicReference<>(new ActiveAnimationGeneration<>(0,
                    AnimatedTextureRegistrySnapshot.EMPTY, Map.of()));
    private static long clientTick;

    private AnimatedTextureTickManager() {
    }

    public static void register() {
        if (!TICK_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTick++;
            ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> active = ACTIVE.get();
            for (AnimatedTexture texture : active.snapshot().all()) {
                texture.tick(CLIENT_TICK_MS);
            }
            uploadChangedFrames(active.bindings());
        });
        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Atlas animation tick manager registered");
    }

    static AnimatedTextureRegistrySnapshot activeSnapshot() {
        return ACTIVE.get().snapshot();
    }

    static void commit(AnimatedTextureReloadAttempt.CommitData data, long reloadSequence) {
        Map<AtlasSpriteKey, TrackedSprite> nextBindings = new java.util.LinkedHashMap<>();
        for (Map.Entry<Identifier, AnimatedTextureReloadAttempt.AtlasCapture> entry : data.atlases().entrySet()) {
            scanAtlas(entry.getKey(), entry.getValue(), data.snapshot(), nextBindings);
        }
        ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> active =
                new ActiveAnimationGeneration<>(reloadSequence, data.snapshot(), nextBindings);
        ACTIVE.set(active);
        uploadChangedFrames(active.bindings());
    }

    private static void scanAtlas(Identifier atlasId, AnimatedTextureReloadAttempt.AtlasCapture pending,
                                  AnimatedTextureRegistrySnapshot snapshot,
                                  Map<AtlasSpriteKey, TrackedSprite> bindings) {
        int registered = 0;
        for (AnimatedTexture texture : snapshot.all()) {
            Sprite matched = null;
            Identifier matchedId = null;
            for (Identifier candidate : texture.getSpriteIdCandidates(atlasId)) {
                Sprite sprite = pending.regions().get(candidate);
                if (sprite != null && candidate.equals(sprite.getContents().getId())) {
                    matched = sprite;
                    matchedId = candidate;
                    break;
                }
            }
            if (matched == null) {
                continue;
            }
            AtlasSpriteKey key = new AtlasSpriteKey(atlasId, matchedId);
            bindings.put(key, new TrackedSprite(pending.atlas(), matched, texture, pending.mipLevel()));
            registered++;
        }
        AnimatedTexturesClient.LOGGER.debug("[AnimatedTextures] Registered {} animated sprites in atlas {}", registered, atlasId);
    }

    private static void uploadChangedFrames(Map<AtlasSpriteKey, TrackedSprite> bindings) {
        for (Map.Entry<AtlasSpriteKey, TrackedSprite> entry : bindings.entrySet()) {
            TrackedSprite binding = entry.getValue();
            long revision = binding.texture().getFrameRevision();
            if (binding.lastUploadedRevision == revision || !binding.retryPolicy.isDue(clientTick)) {
                continue;
            }
            try {
                upload(binding);
                binding.lastUploadedRevision = revision;
                int recoveredFailures = binding.retryPolicy.recordSuccess();
                if (recoveredFailures > 0) {
                    AnimatedTexturesClient.LOGGER.info(
                            "[AnimatedTextures] repair category=upload action=recovered atlas={} sprite={} failures={}",
                            entry.getKey().atlasId(), entry.getKey().spriteId(), recoveredFailures);
                }
            } catch (Exception exception) {
                long retryDelay = binding.retryPolicy.recordFailure(clientTick);
                String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                        ? exception.getClass().getSimpleName()
                        : exception.getClass().getSimpleName() + ": " + exception.getMessage();
                if (binding.retryPolicy.failures() == 1 || isPowerOfTwo(binding.retryPolicy.failures())) {
                    AnimatedTexturesClient.LOGGER.warn(
                            "[AnimatedTextures] repair category=upload action=retry atlas={} sprite={} failure={} retryTicks={} detail={}",
                            entry.getKey().atlasId(), entry.getKey().spriteId(), binding.retryPolicy.failures(),
                            retryDelay, detail);
                }
            }
        }
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & value - 1) == 0;
    }

    private static void upload(TrackedSprite binding) {
        NativeImage baseFrame = null;
        NativeImage[] mipmaps = null;
        try {
            int width = binding.sprite().getContents().getWidth();
            int height = binding.sprite().getContents().getHeight();
            baseFrame = binding.texture().getCurrentFrameResized(width, height);
            mipmaps = MipmapHelper.getMipmapLevelsImages(new NativeImage[]{baseFrame}, binding.mipLevel());
            binding.atlas().bindTexture();
            for (int level = 0; level < mipmaps.length; level++) {
                NativeImage mipmap = mipmaps[level];
                mipmap.upload(level,
                        binding.sprite().getX() >> level,
                        binding.sprite().getY() >> level,
                        0,
                        0,
                        mipmap.getWidth(),
                        mipmap.getHeight(),
                        false,
                        false);
            }
        } finally {
            closeImages(baseFrame, mipmaps);
        }
    }

    private static void closeImages(NativeImage baseFrame, NativeImage[] mipmaps) {
        Set<NativeImage> images = Collections.newSetFromMap(new IdentityHashMap<>());
        if (baseFrame != null) {
            images.add(baseFrame);
        }
        if (mipmaps != null) {
            Collections.addAll(images, mipmaps);
        }
        for (NativeImage image : images) {
            image.close();
        }
    }

    private record AtlasSpriteKey(Identifier atlasId, Identifier spriteId) {
    }

    private static final class TrackedSprite {
        private final SpriteAtlasTexture atlas;
        private final Sprite sprite;
        private final AnimatedTexture texture;
        private final int mipLevel;
        private final UploadRetryPolicy retryPolicy = new UploadRetryPolicy(MAXIMUM_RETRY_DELAY_TICKS);
        private long lastUploadedRevision = Long.MIN_VALUE;

        private TrackedSprite(SpriteAtlasTexture atlas, Sprite sprite, AnimatedTexture texture, int mipLevel) {
            this.atlas = atlas;
            this.sprite = sprite;
            this.texture = texture;
            this.mipLevel = mipLevel;
        }

        private SpriteAtlasTexture atlas() {
            return atlas;
        }

        private Sprite sprite() {
            return sprite;
        }

        private AnimatedTexture texture() {
            return texture;
        }

        private int mipLevel() {
            return mipLevel;
        }
    }
}
