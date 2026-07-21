package com.animatedtextures.util;

import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single-use staging area for one registry reload.
 */
public final class AnimatedTextureRegistryBuilder {

    private final AnimatedTextureReloadBudget budget;
    private final Map<Identifier, AnimatedTexture> textures = new LinkedHashMap<>();
    private boolean frozen;

    public AnimatedTextureRegistryBuilder() {
        this(new AnimatedTextureReloadBudget());
    }

    AnimatedTextureRegistryBuilder(AnimatedTextureReloadBudget budget) {
        this.budget = budget;
    }

    public AnimatedTextureReloadBudget.Remaining remaining() {
        checkMutable();
        return budget.remaining();
    }

    public boolean tryAdd(AnimatedTexture texture) {
        checkMutable();
        Identifier target = texture.getTargetTextureId();
        if (textures.containsKey(target)) {
            return false;
        }
        DecodedAnimation cost = new DecodedAnimation(texture.frames(), texture.getTotalPlays());
        if (!budget.tryReserve(cost)) {
            return false;
        }
        textures.put(target, texture);
        return true;
    }

    public AnimatedTextureRegistrySnapshot freeze() {
        checkMutable();
        frozen = true;
        return new AnimatedTextureRegistrySnapshot(textures, budget.frames(),
                budget.retainedPixels(), budget.estimatedBytes());
    }

    private void checkMutable() {
        if (frozen) {
            throw new IllegalStateException("Registry builder has already been frozen");
        }
    }
}
