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
        long estimatedBytes
) {
    static final AnimatedTextureRegistrySnapshot EMPTY =
            new AnimatedTextureRegistrySnapshot(Map.of(), 0, 0, 0);

    public AnimatedTextureRegistrySnapshot {
        textures = Map.copyOf(textures);
    }

    public AnimatedTexture get(Identifier targetId) {
        return textures.get(targetId);
    }

    public boolean has(Identifier targetId) {
        return textures.containsKey(targetId);
    }

    public Collection<AnimatedTexture> all() {
        return List.copyOf(textures.values());
    }

    public int size() {
        return textures.size();
    }
}
