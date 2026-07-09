package com.animatedtextures.mixin;

import com.animatedtextures.util.AnimatedTextureRegistry;
import com.animatedtextures.util.AnimatedTextureTickManager;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks TextureManager to detect when our animated textures are accessed
 * and start the tick manager.
 *
 * Note: block/item textures live in the sprite atlas (TextureAtlasSprite),
 * not as individual textures in TextureManager. Our animation is driven by
 * the tick manager + SpriteContentsAnimationMixin which intercepts the atlas
 * upload call per-sprite.
 *
 * This mixin just ensures the tick manager starts when any texture loads.
 */
@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {

    @Inject(
            method = "getTexture(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/texture/AbstractTexture;",
            at = @At("RETURN")
    )
    private void onGetTexture(Identifier id, CallbackInfoReturnable<AbstractTexture> cir) {
        // Ensure tick manager is running whenever any texture is accessed.
        // Use size() > 0 instead of !all().isEmpty() to avoid allocating a new collection
        // on every getTexture() call (which fires thousands of times per frame).
        if (AnimatedTextureRegistry.INSTANCE.size() > 0) {
            AnimatedTextureTickManager.register();
        }
    }
}
