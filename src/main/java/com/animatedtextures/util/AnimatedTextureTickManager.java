package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesConfig;
import com.animatedtextures.client.AnimatedTexturesClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.texture.MipmapHelper;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks exact stitched sprite bindings and uploads changed animation frames.
 */
public final class AnimatedTextureTickManager {

    private static final long INITIAL_RETRY_DELAY_NANOS = 50_000_000L;
    private static final long MAXIMUM_RETRY_DELAY_NANOS = 5_000_000_000L;
    private static final AtomicBoolean TICK_REGISTERED = new AtomicBoolean();
    private static final AtomicReference<ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite>> ACTIVE =
            new AtomicReference<>(new ActiveAnimationGeneration<>(0,
                    AnimatedTextureRegistrySnapshot.EMPTY, Map.of(),
                    new PreparedFrameCache(0), new AnimationFrameScheduler()));

    private AnimatedTextureTickManager() {
    }

    public static void register() {
        if (!TICK_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> active = ACTIVE.get();
            if (!active.snapshot().quality().isRenderFrameDriven()) {
                advanceAndUpload(active, Util.getMeasuringTimeNano());
            }
        });
        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Atlas animation tick manager registered");
    }

    static AnimatedTextureRegistrySnapshot activeSnapshot() {
        return ACTIVE.get().snapshot();
    }

    public static void onRenderFrame() {
        ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> active = ACTIVE.get();
        if (!active.snapshot().quality().isRenderFrameDriven()) {
            return;
        }
        RenderSystem.assertOnRenderThreadOrInit();
        advanceAndUpload(active, Util.getMeasuringTimeNano());
    }

    public static void invalidatePreparedFrames() {
        RenderSystem.assertOnRenderThreadOrInit();
        ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> active = ACTIVE.get();
        active.frameCache().close();
        for (TrackedSprite binding : active.bindings().values()) {
            binding.lastUploadedFrame = Integer.MIN_VALUE;
            binding.lastScalingMode = null;
        }
    }

    static void commit(AnimatedTextureReloadAttempt.CommitData data, long reloadSequence) {
        Map<AtlasSpriteKey, TrackedSprite> nextBindings = new java.util.LinkedHashMap<>();
        for (Map.Entry<Identifier, AnimatedTextureReloadAttempt.AtlasCapture> entry : data.atlases().entrySet()) {
            scanAtlas(entry.getKey(), entry.getValue(), data.snapshot(), nextBindings);
        }
        ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> active =
                new ActiveAnimationGeneration<>(reloadSequence, data.snapshot(), nextBindings,
                        new PreparedFrameCache(data.snapshot().quality().preparedFrameCacheBytes()),
                        new AnimationFrameScheduler(data.snapshot().quality().isRenderFrameDriven()
                                ? AnimationFrameScheduler.TARGET_INTERVAL_NANOS : 1));
        ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> previous = ACTIVE.getAndSet(active);
        RenderSystem.assertOnRenderThreadOrInit();
        closeGeneration(previous);
        long nowNanos = Util.getMeasuringTimeNano();
        active.frameScheduler().pollElapsedMillis(nowNanos);
        uploadChangedFrames(active, nowNanos);
    }

    private static void advanceAndUpload(ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> active,
                                         long nowNanos) {
        long elapsedMs = active.frameScheduler().pollElapsedMillis(nowNanos);
        if (elapsedMs <= 0) {
            return;
        }
        for (AnimatedTexture texture : active.snapshot().all()) {
            texture.tick(elapsedMs);
        }
        uploadChangedFrames(active, nowNanos);
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

    private static void uploadChangedFrames(ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> active,
                                            long nowNanos) {
        AnimatedTexturesConfig.ScalingMode scalingMode = AnimatedTexturesConfig.get().scalingMode;
        SpriteAtlasTexture boundAtlas = null;
        for (Map.Entry<AtlasSpriteKey, TrackedSprite> entry : active.bindings().entrySet()) {
            TrackedSprite binding = entry.getValue();
            int frameIndex = binding.texture().getCurrentFrameIndex();
            if ((binding.lastUploadedFrame == frameIndex && binding.lastScalingMode == scalingMode)
                    || !binding.retryPolicy.isDue(nowNanos)) {
                continue;
            }
            try {
                if (boundAtlas != binding.atlas()) {
                    binding.atlas().bindTexture();
                    boundAtlas = binding.atlas();
                }
                upload(binding, active.frameCache(), scalingMode);
                binding.lastUploadedFrame = frameIndex;
                binding.lastScalingMode = scalingMode;
                int recoveredFailures = binding.retryPolicy.recordSuccess();
                if (recoveredFailures > 0) {
                    AnimatedTexturesClient.LOGGER.info(
                            "[AnimatedTextures] repair category=upload action=recovered atlas={} sprite={} failures={}",
                            entry.getKey().atlasId(), entry.getKey().spriteId(), recoveredFailures);
                }
            } catch (Exception exception) {
                long retryDelay = binding.retryPolicy.recordFailure(nowNanos);
                String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                        ? exception.getClass().getSimpleName()
                        : exception.getClass().getSimpleName() + ": " + exception.getMessage();
                if (binding.retryPolicy.failures() == 1 || isPowerOfTwo(binding.retryPolicy.failures())) {
                    AnimatedTexturesClient.LOGGER.warn(
                            "[AnimatedTextures] repair category=upload action=retry atlas={} sprite={} failure={} retryMs={} detail={}",
                            entry.getKey().atlasId(), entry.getKey().spriteId(), binding.retryPolicy.failures(),
                            retryDelay / 1_000_000L, detail);
                }
            }
        }
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & value - 1) == 0;
    }

    private static void upload(TrackedSprite binding, PreparedFrameCache cache,
                               AnimatedTexturesConfig.ScalingMode scalingMode) {
        int width = binding.sprite().getContents().getWidth();
        int height = binding.sprite().getContents().getHeight();
        PreparedFrameCache.Key key = new PreparedFrameCache.Key(binding.texture(),
                binding.texture().getCurrentFrameIndex(), width, height, binding.mipLevel(), scalingMode);
        NativeImage[] mipmaps;
        boolean cached = cache.canCache(key);
        if (cached) {
            mipmaps = cache.getOrCreate(key, () -> createMipmaps(binding.texture(), width, height,
                    binding.mipLevel(), scalingMode));
        } else {
            if (binding.scratchFrame == null) {
                binding.scratchFrame = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            }
            binding.texture().renderCurrentFrame(binding.scratchFrame, scalingMode);
            mipmaps = MipmapHelper.getMipmapLevelsImages(new NativeImage[]{binding.scratchFrame}, binding.mipLevel());
        }
        try {
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
            if (!cached) {
                closeTemporaryMipmaps(binding.scratchFrame, mipmaps);
            }
        }
    }

    private static NativeImage[] createMipmaps(AnimatedTexture texture, int width, int height, int mipLevel,
                                                AnimatedTexturesConfig.ScalingMode scalingMode) {
        NativeImage baseFrame = texture.getCurrentFrameResized(width, height, scalingMode);
        try {
            return MipmapHelper.getMipmapLevelsImages(new NativeImage[]{baseFrame}, mipLevel);
        } catch (RuntimeException exception) {
            baseFrame.close();
            throw exception;
        }
    }

    private static void closeTemporaryMipmaps(NativeImage scratchFrame, NativeImage[] mipmaps) {
        for (NativeImage mipmap : mipmaps) {
            if (mipmap != scratchFrame) {
                mipmap.close();
            }
        }
    }

    private static void closeGeneration(ActiveAnimationGeneration<AtlasSpriteKey, TrackedSprite> generation) {
        generation.frameCache().close();
        for (TrackedSprite binding : generation.bindings().values()) {
            if (binding.scratchFrame != null) {
                binding.scratchFrame.close();
            }
        }
    }

    private record AtlasSpriteKey(Identifier atlasId, Identifier spriteId) {
    }

    private static final class TrackedSprite {
        private final SpriteAtlasTexture atlas;
        private final Sprite sprite;
        private final AnimatedTexture texture;
        private final int mipLevel;
        private final UploadRetryPolicy retryPolicy = new UploadRetryPolicy(
                INITIAL_RETRY_DELAY_NANOS, MAXIMUM_RETRY_DELAY_NANOS);
        private NativeImage scratchFrame;
        private int lastUploadedFrame = Integer.MIN_VALUE;
        private AnimatedTexturesConfig.ScalingMode lastScalingMode;

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
