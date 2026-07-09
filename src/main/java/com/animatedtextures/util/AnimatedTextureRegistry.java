package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry that holds all loaded AnimatedTexture instances.
 * Keyed by the TARGET identifier (i.e., the .png path Minecraft would use).
 *
 * Thread-safety: reads happen on the render thread; writes during reload.
 */
public class AnimatedTextureRegistry {

    public static final AnimatedTextureRegistry INSTANCE = new AnimatedTextureRegistry();

    private final Map<Identifier, AnimatedTexture> registry = new ConcurrentHashMap<>();

    private AnimatedTextureRegistry() {}

    public void clear() {
        registry.clear();
    }

    /**
     * Decode a GIF from the given input stream and register it.
     * For mob_effect textures, registers under both the standard ID and the
     * bare registry-name ID (used by the mob_effects atlas with prefix="").
     */
    public void registerGif(Identifier sourceId, InputStream stream) throws Exception {
        GifDecoder decoder = new GifDecoder();
        List<AnimatedFrame> frames = decoder.decode(stream);
        if (frames.isEmpty()) {
            AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] GIF had no frames: {}", sourceId);
            return;
        }
        AnimatedTexture texture = new AnimatedTexture(sourceId, frames);
        registerTexture(texture);
    }

    /**
     * Decode an APNG (.png3) from the given input stream and register it.
     * For mob_effect textures, registers under both the standard ID and the
     * bare registry-name ID (used by the mob_effects atlas with prefix="").
     */
    public void registerApng(Identifier sourceId, InputStream stream) throws Exception {
        ApngDecoder decoder = new ApngDecoder();
        List<AnimatedFrame> frames = decoder.decode(stream);
        if (frames.isEmpty()) {
            AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] APNG had no frames: {}", sourceId);
            return;
        }
        AnimatedTexture texture = new AnimatedTexture(sourceId, frames);
        registerTexture(texture);
    }

    private void registerTexture(AnimatedTexture texture) {
        Identifier target = texture.getTargetTextureId();
        registry.put(target, texture);
        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Registry: source={} -> target={} ({} frames)",
                texture.getSourceId(), target, texture.getFrameCount());

        // mob_effect atlas uses prefix="" so sprite IDs are bare registry names.
        // Register under that ID too so the sprite can be found.
        Identifier mobEffectTarget = texture.getMobEffectTargetId();
        if (mobEffectTarget != null) {
            registry.put(mobEffectTarget, texture);
            AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Registry: {} -> {} (mob_effect alias)",
                    texture.getSourceId(), mobEffectTarget);
        }
    }

    /**
     * Look up an animated texture by its TARGET identifier (the .png path).
     * Returns null if no animated texture is registered for this id.
     */
    public AnimatedTexture get(Identifier targetId) {
        return registry.get(targetId);
    }

    /**
     * Returns true if the given identifier has a registered animated texture.
     */
    public boolean has(Identifier targetId) {
        return registry.containsKey(targetId);
    }

    /**
     * All registered animated textures.
     */
    public Collection<AnimatedTexture> all() {
        return registry.values();
    }

    /**
     * Number of registered animated textures.
     */
    public int size() {
        return registry.size();
    }
}
