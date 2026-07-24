package com.animatedtextures.client;

import com.animatedtextures.util.AnimatedTexture;
import com.animatedtextures.util.AnimationQuality;
import com.animatedtextures.util.AnimatedTextureRegistryBuilder;
import com.animatedtextures.util.AnimatedTextureRegistrySnapshot;
import com.animatedtextures.util.AnimatedTextureReloadAttempt;
import com.animatedtextures.util.AnimatedTextureReloadCoordinator;
import com.animatedtextures.util.ApngDecoder;
import com.animatedtextures.util.DecodedAnimation;
import com.animatedtextures.util.GifDecoder;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.List;

public final class AnimatedTextureReloadListener implements SimpleSynchronousResourceReloadListener {

    private static final Identifier ID = Identifier.of("animated_textures", "reload_listener");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Scanning visible resource-pack animations");
        AnimatedTextureReloadAttempt attempt = AnimatedTextureReloadCoordinator.currentAttempt();
        if (attempt == null) {
            throw new IllegalStateException("Animated texture reload listener has no owning reload attempt");
        }
        reloadTransactional(manager, attempt);
    }

    private void reloadTransactional(ResourceManager manager, AnimatedTextureReloadAttempt attempt) {
        List<AnimatedResourceResolver.SelectedResource> selections = AnimatedResourceResolver.resolve(manager);
        AnimationQuality quality = AnimatedTexturesConfig.get().quality;
        AnimatedTextureRegistryBuilder builder = new AnimatedTextureRegistryBuilder(quality);
        int loaded = 0;
        for (AnimatedResourceResolver.SelectedResource selection : selections) {
            if (!builder.remaining().canDecode()) {
                AnimatedTexturesClient.LOGGER.warn(
                        "[AnimatedTextures] repair category=resource action=remaining_skipped count={} reason=reload_budget_exhausted",
                        selections.size() - loaded);
                break;
            }
            try (InputStream input = selection.resource().getInputStream()) {
                var remaining = builder.remaining();
                DecodedAnimation animation = switch (selection.format()) {
                    case GIF -> new GifDecoder(quality, remaining).decodeAnimation(input);
                    case APNG -> new ApngDecoder(quality, remaining).decodeAnimation(input);
                };
                AnimatedTexture texture = new AnimatedTexture(selection.sourceId(), animation, quality);
                if (builder.tryAdd(texture)) {
                    loaded++;
                    AnimatedTexturesClient.LOGGER.info(
                            "[AnimatedTextures] Loaded {} target={} pack={}",
                            selection.format(), selection.fallbackId(), selection.resource().getPackId());
                } else {
                    AnimatedTexturesClient.LOGGER.warn(
                            "[AnimatedTextures] repair category=resource action=static_fallback target={} source={} pack={} reason=reload_budget_or_duplicate",
                            selection.fallbackId(), selection.sourceId(), selection.resource().getPackId());
                }
            } catch (Exception exception) {
                AnimatedTexturesClient.LOGGER.warn(
                        "[AnimatedTextures] repair category=resource action=static_fallback target={} source={} pack={} reason={}",
                        selection.fallbackId(), selection.sourceId(), selection.resource().getPackId(), exception.getMessage());
            }
        }

        AnimatedTextureRegistrySnapshot snapshot = builder.freeze();
        attempt.stageSnapshot(snapshot);
        AnimatedTexturesClient.LOGGER.info(
                "[AnimatedTextures] Loaded {} animation(s) from {} eligible target(s), retainedFrames={}, retainedPixels={}, estimatedBytes={}",
                loaded, selections.size(), snapshot.frameCount(), snapshot.retainedPixels(), snapshot.estimatedBytes());
    }
}
