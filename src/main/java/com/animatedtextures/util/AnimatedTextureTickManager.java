package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;

import net.minecraft.util.Identifier;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnimatedTextureTickManager {

    private static final AtomicBoolean tickRegistered = new AtomicBoolean(false);
    private static final List<WeakReference<SpriteContents>> trackedSprites = new ArrayList<>();

    // Store ALL atlases that have been uploaded (block, mob_effects, GUI, etc.)
    // Key: atlas Identifier, Value: atlas reference
    private static final Map<Identifier, SpriteAtlasTexture> pendingAtlases = new ConcurrentHashMap<>();

    // Tracks sprites that were already uploaded by the SpriteContentsAnimationMixin this tick.
    // Prevents double-uploading when both the mixin path and tick-manager path fire.
    private static final Set<Identifier> mixinHandledSprites = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Called by SpriteAtlasTextureMixin after any atlas is built.
     * Stores the atlas for scanning after the reload listener populates the registry.
     */
    public static void scheduleAtlasScan(SpriteAtlasTexture atlas) {
        pendingAtlases.put(atlas.getId(), atlas);
        // Clear old tracked sprites — they belong to the previous atlas
        synchronized (trackedSprites) {
            trackedSprites.clear();
        }
        ensureTickRegistered();
    }

    /**
     * Called from reload listener after registry is populated.
     * Scans all pending atlases immediately.
     */
    public static void onRegistryReady() {
        Map<Identifier, SpriteAtlasTexture> atlases = new ConcurrentHashMap<>(pendingAtlases);
        pendingAtlases.clear();
        for (Map.Entry<Identifier, SpriteAtlasTexture> entry : atlases.entrySet()) {
            scanAtlas(entry.getValue());
        }
    }

    /**
     * Check if a sprite is the atlas's "missing" fallback (minecraft:missingno).
     * SpriteAtlasTexture.getSprite() NEVER returns null — it returns missingno
     * for any ID not in the atlas. We must filter these out.
     */
    private static boolean isRealSprite(net.minecraft.client.texture.Sprite sprite, Identifier expectedId) {
        if (sprite == null) return false;
        Identifier actualId = sprite.getContents().getId();
        return expectedId.equals(actualId);
    }

    private static void scanAtlas(SpriteAtlasTexture atlas) {
        int count = 0;
        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] scanAtlas: scanning atlas '{}' for {} registered texture(s).",
                atlas.getId(), AnimatedTextureRegistry.INSTANCE.size());
        for (AnimatedTexture tex : AnimatedTextureRegistry.INSTANCE.all()) {
            Identifier targetId = tex.getTargetTextureId();
            // Try the standard target ID first
            var sprite = atlas.getSprite(targetId);
            if (!isRealSprite(sprite, targetId)) sprite = null;
            if (sprite != null) {
                AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] scanAtlas: found {} in atlas '{}' via targetId={}",
                        tex.getSourceId(), atlas.getId(), targetId);
            }
            // If not found, try the mob_effect bare-name alias
            if (sprite == null) {
                var mobEffectId = tex.getMobEffectTargetId();
                if (mobEffectId != null) {
                    sprite = atlas.getSprite(mobEffectId);
                    if (!isRealSprite(sprite, mobEffectId)) sprite = null;
                    if (sprite != null) {
                        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] scanAtlas: found {} in atlas '{}' via mobEffectId={}",
                                tex.getSourceId(), atlas.getId(), mobEffectId);
                    }
                }
            }
            if (sprite == null) {
                AnimatedTexturesClient.LOGGER.debug("[AnimatedTextures] scanAtlas: not in atlas '{}' for {} (targetId={}, mobEffectId={})",
                        atlas.getId(), tex.getSourceId(), targetId, tex.getMobEffectTargetId());
            }
            if (sprite != null) {
                registerSprite(sprite.getContents());
                count++;
            }
        }
        if (count > 0) {
            AnimatedTexturesClient.LOGGER.info(
                "[AnimatedTextures] Registered {} animated sprite(s) from atlas '{}'.",
                count, atlas.getId());
        } else {
            AnimatedTexturesClient.LOGGER.debug(
                "[AnimatedTextures] No matching sprites found in atlas '{}'.", atlas.getId());
        }
    }

    public static void register() {
        ensureTickRegistered();
    }

    private static void ensureTickRegistered() {
        if (!tickRegistered.compareAndSet(false, true)) return;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Clear the set of sprites handled by the mixin this tick.
            // This ensures we don't carry over stale entries between ticks.
            mixinHandledSprites.clear();

            // If any atlas scans were deferred, run them now (registry is populated by this point)
            if (!pendingAtlases.isEmpty()) {
                Map<Identifier, SpriteAtlasTexture> atlases = new ConcurrentHashMap<>(pendingAtlases);
                pendingAtlases.clear();
                for (Map.Entry<Identifier, SpriteAtlasTexture> entry : atlases.entrySet()) {
                    scanAtlas(entry.getValue());
                }
            }

            if (client.world == null) return;
            long now = System.currentTimeMillis();

            // Advance frame counters
            for (AnimatedTexture tex : AnimatedTextureRegistry.INSTANCE.all()) {
                tex.tick(now);
            }

            // Upload changed frames to GPU (skipping sprites handled by the mixin)
            uploadAll(client);
        });

        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Tick manager registered.");
    }

    private static void uploadAll(net.minecraft.client.MinecraftClient client) {
        // Try to get all available atlases from the texture manager
        List<SpriteAtlasTexture> atlases = new ArrayList<>();

        // Block atlas (always present)
        var blockAtlas = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        if (blockAtlas instanceof SpriteAtlasTexture atlas) {
            atlases.add(atlas);
        }

        // Mob effects atlas
        var mobEffectsAtlas = client.getTextureManager().getTexture(
                Identifier.ofVanilla("textures/atlas/mob_effects.png"));
        if (mobEffectsAtlas instanceof SpriteAtlasTexture atlas) {
            atlases.add(atlas);
        }

        // GUI atlas
        var guiAtlas = client.getTextureManager().getTexture(
                Identifier.ofVanilla("textures/atlas/gui.png"));
        if (guiAtlas instanceof SpriteAtlasTexture atlas) {
            atlases.add(atlas);
        }

        synchronized (trackedSprites) {
            Iterator<WeakReference<SpriteContents>> it = trackedSprites.iterator();
            while (it.hasNext()) {
                SpriteContents contents = it.next().get();
                if (contents == null) { it.remove(); continue; }

                Identifier spriteId = contents.getId();
                AnimatedTexture tex = AnimatedTextureRegistry.INSTANCE.get(spriteId);
                if (tex == null) {
                    it.remove(); continue;
                }

                // Skip sprites that were already uploaded by SpriteContentsAnimationMixin this tick
                if (mixinHandledSprites.contains(spriteId)) continue;

                // Find the sprite in any atlas and upload the resized frame
                NativeImage frame = null;
                try {
                    int spriteW = contents.getWidth();
                    int spriteH = contents.getHeight();
                    // Resize animated frame to match the sprite's allocated atlas region.
                    // This supports high-resolution resource packs where the sprite size
                    // may differ from the animated texture's native frame size.
                    frame = tex.getCurrentFrameResized(spriteW, spriteH);

                    boolean uploaded = false;
                    for (SpriteAtlasTexture atlas : atlases) {
                        var sprite = atlas.getSprite(spriteId);
                        // getSprite() returns missingno fallback instead of null — verify identity
                        if (sprite != null && !sprite.getContents().getId().equals(spriteId)) {
                            sprite = null;
                        }
                        if (sprite != null) {
                            atlas.bindTexture();
                            frame.upload(0, sprite.getX(), sprite.getY(), 0, 0,
                                    spriteW, spriteH, false, true);
                            uploaded = true;
                            AnimatedTexturesClient.LOGGER.debug(
                                "[AnimatedTextures] uploadAll: uploaded {} to atlas '{}' at ({},{}) {}x{}",
                                spriteId, atlas.getId(), sprite.getX(), sprite.getY(), spriteW, spriteH);
                            break;
                        }
                    }

                    if (!uploaded) {
                        AnimatedTexturesClient.LOGGER.info(
                            "[AnimatedTextures] uploadAll: sprite {} NOT found in any atlas, removing from tracking.", spriteId);
                        it.remove();
                    }
                } catch (Exception e) {
                    // Not ready yet, retry next tick
                    AnimatedTexturesClient.LOGGER.info(
                        "[AnimatedTextures] uploadAll: upload failed for {}: {}", spriteId, e.getMessage());
                } finally {
                    // Ensure NativeImage is always closed to prevent GPU memory leaks
                    if (frame != null) {
                        frame.close();
                    }
                }
            }
        }
    }

    /**
     * Called by SpriteContentsAnimationMixin when it successfully uploads a frame.
     * Marks the sprite so the tick manager skips it this tick, preventing double upload.
     */
    public static void markMixinHandled(Identifier spriteId) {
        mixinHandledSprites.add(spriteId);
    }

    public static void registerSprite(SpriteContents sprite) {
        synchronized (trackedSprites) {
            for (WeakReference<SpriteContents> ref : trackedSprites) {
                if (ref.get() == sprite) return;
            }
            trackedSprites.add(new WeakReference<>(sprite));
            AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Tracking sprite: {}", sprite.getId());
        }
    }

    public static void reset() {
        synchronized (trackedSprites) {
            trackedSprites.clear();
        }
        mixinHandledSprites.clear();
        // NOTE: Do NOT clear pendingAtlases here! It is set by the mixin during
        // atlas upload (which fires just before reload listeners). If we clear
        // it, onRegistryReady() will find nothing and skip the scan entirely.
        // The tick callback or onRegistryReady() will consume it atomically.
    }
}
