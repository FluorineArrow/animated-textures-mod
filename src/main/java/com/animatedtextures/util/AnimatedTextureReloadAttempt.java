package com.animatedtextures.util;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reload-scoped staging state. Identity, rather than a numeric generation, owns all mutations.
 */
public final class AnimatedTextureReloadAttempt {

    public record AtlasCapture(SpriteAtlasTexture atlas, Map<Identifier, Sprite> regions, int mipLevel) {
        public AtlasCapture {
            regions = Map.copyOf(regions);
        }
    }

    private enum State {
        OPEN, COMMITTED, ABORTED, SUPERSEDED
    }

    private final long sequence;
    private final Map<Identifier, AtlasCapture> atlases = new LinkedHashMap<>();
    private AnimatedTextureRegistrySnapshot snapshot;
    private State state = State.OPEN;

    AnimatedTextureReloadAttempt(long sequence) {
        this.sequence = sequence;
    }

    public long sequence() {
        return sequence;
    }

    public synchronized void recordAtlas(SpriteAtlasTexture atlas, Map<Identifier, Sprite> regions, int mipLevel) {
        if (state == State.OPEN) {
            atlases.put(atlas.getId(), new AtlasCapture(atlas, regions, mipLevel));
        }
    }

    public synchronized void stageSnapshot(AnimatedTextureRegistrySnapshot stagedSnapshot) {
        if (state != State.OPEN || snapshot != null) {
            throw new IllegalStateException("Reload attempt cannot stage another snapshot");
        }
        snapshot = stagedSnapshot;
    }

    synchronized CommitData commitData() {
        if (state != State.OPEN || snapshot == null) {
            return null;
        }
        state = State.COMMITTED;
        return new CommitData(snapshot, Map.copyOf(atlases));
    }

    synchronized void abort(boolean superseded) {
        if (state == State.OPEN) {
            state = superseded ? State.SUPERSEDED : State.ABORTED;
            atlases.clear();
            snapshot = null;
        }
    }

    record CommitData(AnimatedTextureRegistrySnapshot snapshot,
                      Map<Identifier, AtlasCapture> atlases) {
    }
}
