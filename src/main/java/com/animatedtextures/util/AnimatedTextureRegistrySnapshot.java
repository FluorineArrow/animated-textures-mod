package com.animatedtextures.util;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Immutable canonical animation registry staged by one completed listener.
 */
public record AnimatedTextureRegistrySnapshot(
        Map<Identifier, AnimatedTexture> textures,
        int frameCount,
        long retainedPixels,
        long estimatedBytes,
        AnimationQuality quality
) {
    static final AnimatedTextureRegistrySnapshot EMPTY =
            new AnimatedTextureRegistrySnapshot(Map.of(), 0, 0, 0, AnimationQuality.STANDARD);
    private static final Collection<AnimatedTexture> EMPTY_TEXTURES = List.of();

    public AnimatedTextureRegistrySnapshot(Map<Identifier, AnimatedTexture> textures, int frameCount,
                                           long retainedPixels, long estimatedBytes) {
        this(textures, frameCount, retainedPixels, estimatedBytes, AnimationQuality.STANDARD);
    }

    public AnimatedTextureRegistrySnapshot {
        textures = Map.copyOf(textures);
        quality = java.util.Objects.requireNonNull(quality, "quality");
    }

    public AnimatedTexture get(Identifier targetId) {
        return textures.get(targetId);
    }

    public boolean has(Identifier targetId) {
        return textures.containsKey(targetId);
    }

    public Collection<AnimatedTexture> all() {
        return textures.isEmpty() ? EMPTY_TEXTURES : textures.values();
    }

    public int size() {
        return textures.size();
    }
}
