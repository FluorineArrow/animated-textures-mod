package com.animatedtextures.util;

import net.minecraft.util.Identifier;

import java.util.Collection;

/**
 * Read-only view of the atomically active animation generation.
 */
public final class AnimatedTextureRegistry {

    public static final AnimatedTextureRegistry INSTANCE = new AnimatedTextureRegistry();

    private AnimatedTextureRegistry() {
    }

    public AnimatedTexture get(Identifier targetId) {
        return AnimatedTextureTickManager.activeSnapshot().get(targetId);
    }

    public boolean has(Identifier targetId) {
        return AnimatedTextureTickManager.activeSnapshot().has(targetId);
    }

    public Collection<AnimatedTexture> all() {
        return AnimatedTextureTickManager.activeSnapshot().all();
    }

    public int size() {
        return AnimatedTextureTickManager.activeSnapshot().size();
    }
}
