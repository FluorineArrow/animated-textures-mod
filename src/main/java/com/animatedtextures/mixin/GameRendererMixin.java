package com.animatedtextures.mixin;

import com.animatedtextures.util.AnimatedTextureTickManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void animatedTextures$onRenderFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo info) {
        AnimatedTextureTickManager.onRenderFrame();
    }
}
