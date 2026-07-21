package com.animatedtextures.mixin;

import com.animatedtextures.client.AnimatedTexturesClient;
import com.animatedtextures.util.AnimatedTextureReloadAttempt;
import com.animatedtextures.util.AnimatedTextureReloadCoordinator;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Schedules a deferred atlas scan for after the reload listeners finish.
 * The atlas is built BEFORE reload listeners run, so we can't scan immediately.
 * Instead we store the atlas reference and scan on the first client tick.
 *
 * This hooks into ALL sprite atlases (block, mob_effects, GUI, etc.) so that
 * animated textures work in every atlas — not just the block atlas.
 *
 * Sodium compatibility: Uses @Mixin priority 1001 to ensure our injector runs
 * after Sodium's MixinSpriteAtlasTexture (default priority 1000). This prevents
 * Sodium's texture tracking from interfering with our atlas scan scheduling.
 * Sodium 0.6.0+ fixed an issue where mixin overrides from other mods would not
 * apply; our high priority guarantees we run alongside Sodium's hooks.
 */
@Mixin(value = SpriteAtlasTexture.class, priority = 1001)
public abstract class SpriteAtlasTextureMixin {

    @Inject(method = "upload", at = @At("TAIL"))
    private void onUpload(SpriteLoader.StitchResult stitchResult, CallbackInfo ci) {
        SpriteAtlasTexture self = (SpriteAtlasTexture)(Object)this;
        AnimatedTextureReloadAttempt attempt = AnimatedTextureReloadCoordinator.currentAttempt();
        if (attempt != null) {
            attempt.recordAtlas(self, stitchResult.regions(), stitchResult.mipLevel());
        }
        AnimatedTexturesClient.LOGGER.debug(
            "[AnimatedTextures] Atlas '{}' upload complete for reload attempt {}.",
            self.getId(), attempt == null ? "none" : attempt.sequence());
    }
}
