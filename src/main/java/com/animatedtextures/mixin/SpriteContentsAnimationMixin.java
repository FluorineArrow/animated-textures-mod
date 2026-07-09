package com.animatedtextures.mixin;

import com.animatedtextures.client.AnimatedTexturesClient;
import com.animatedtextures.util.AnimatedTexture;
import com.animatedtextures.util.AnimatedTextureRegistry;
import com.animatedtextures.util.AnimatedTextureTickManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts SpriteContents.upload() to push GIF/APNG frames into the atlas.
 *
 * upload() is called:
 *   - Once on initial atlas stitch (we push frame 0)
 *   - Every game tick by Minecraft's Animator for .mcmeta animated sprites
 *
 * For our textures we cancel the vanilla call and push our own frame.
 * The tick manager separately handles sprites without vanilla animation via
 * direct NativeImage.upload() to the GPU texture.
 *
 * Sodium compatibility: Sodium's "Animate Only Visible Textures" feature
 * tracks sprite visibility via SpriteAtlasTexture.getSprite() mixins.
 * Our mod bypasses visibility tracking by uploading directly to the GPU
 * texture, so animated textures continue to animate regardless of that setting.
 */
@Mixin(SpriteContents.class)
public abstract class SpriteContentsAnimationMixin {

    @Shadow
    public abstract Identifier getId();

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Inject(
            method = "upload(IIII[Lnet/minecraft/client/texture/NativeImage;)V",
            at = @At("HEAD")
    )
    private void onUpload(int atlasX, int atlasY, int frameX, int frameY,
                          NativeImage[] uploadImages, CallbackInfo ci) {
        Identifier id = getId();
        if (id == null) return;
        AnimatedTexture animTex = AnimatedTextureRegistry.INSTANCE.get(id);
        if (animTex == null) return;

        // Validate we have valid image data.
        if (uploadImages == null || uploadImages.length == 0 || uploadImages[0] == null) {
            return;
        }

        // uploadImages[0] is the sprite's own mip-level image (e.g., 16×16),
        // NOT the full atlas. Vanilla will upload it to the atlas at (atlasX, atlasY).
        // We replace the sprite-local pixel data and let vanilla handle the atlas upload.
        NativeImage spriteImage = uploadImages[0];
        int spriteW = getWidth();
        int spriteH = getHeight();

        // Ensure the sprite image dimensions match
        if (spriteImage.getWidth() != spriteW || spriteImage.getHeight() != spriteH) {
            return;
        }

        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Mixin: replacing sprite {} (frame {})",
                id, animTex.getCurrentFrameIndex());

        NativeImage frame = null;
        try {
            frame = animTex.getCurrentFrameResized(spriteW, spriteH);
            for (int py = 0; py < spriteH; py++) {
                for (int px = 0; px < spriteW; px++) {
                    spriteImage.setColor(px, py, frame.getColor(px, py));
                }
            }
            // Mark this sprite so the tick manager skips it this tick (prevents double upload)
            AnimatedTextureTickManager.markMixinHandled(id);
        } catch (Exception e) {
            AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] Mixin: frame replace failed for {}: {}", id, e.getMessage());
        } finally {
            if (frame != null) {
                frame.close();
            }
        }
        // Do NOT ci.cancel() — let vanilla upload the modified sprite data to the atlas
    }
}

