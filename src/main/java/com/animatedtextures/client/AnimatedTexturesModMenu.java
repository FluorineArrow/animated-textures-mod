package com.animatedtextures.client;

import com.animatedtextures.util.AnimatedTextureRegistry;
import com.animatedtextures.util.AnimatedTextureTickManager;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * ModMenu integration for supported Animated Textures settings.
 */
public final class AnimatedTexturesModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AnimatedTexturesConfigScreen::new;
    }

    private static final class AnimatedTexturesConfigScreen extends Screen {

        private final Screen parent;
        private final AnimatedTexturesConfig draft;

        private AnimatedTexturesConfigScreen(Screen parent) {
            super(Text.literal("Animated Textures"));
            this.parent = parent;
            this.draft = AnimatedTexturesConfig.get().copy();
        }

        @Override
        protected void init() {
            int centerX = width / 2;
            int startY = height / 2 - 56;
            int buttonWidth = 200;
            int buttonHeight = 20;

            addDrawableChild(ButtonWidget.builder(Text.literal("Animated Textures - Settings"), button -> {
            }).dimensions(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Loaded: " + AnimatedTextureRegistry.INSTANCE.size() + " animated texture(s)"), button -> {
            }).dimensions(centerX - buttonWidth / 2, startY + 24, buttonWidth, buttonHeight).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Scaling: " + draft.scalingMode.getDisplayName()), button -> {
                draft.scalingMode = draft.scalingMode.next();
                button.setMessage(Text.literal("Scaling: " + draft.scalingMode.getDisplayName()));
            }).dimensions(centerX - buttonWidth / 2, startY + 52, buttonWidth, buttonHeight).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Quality: " + draft.quality.getDisplayName()), button -> {
                draft.quality = draft.quality.next();
                button.setMessage(Text.literal("Quality: " + draft.quality.getDisplayName()));
            }).dimensions(centerX - buttonWidth / 2, startY + 76, buttonWidth, buttonHeight).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Save & Done"), button -> {
                AnimatedTexturesConfig previous = AnimatedTexturesConfig.get();
                boolean qualityChanged = previous.quality != draft.quality;
                boolean scalingChanged = previous.scalingMode != draft.scalingMode;
                AnimatedTexturesConfig.replaceAndSave(draft);
                if (qualityChanged) {
                    MinecraftClient.getInstance().reloadResources();
                } else if (scalingChanged) {
                    AnimatedTextureTickManager.invalidatePreparedFrames();
                }
                close();
            }).dimensions(centerX - 50, startY + 116, 100, buttonHeight).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);

            int centerX = width / 2;
            int startY = height / 2 - 56;
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Place paired .gif or .png3 files in your resource pack."),
                    centerX, startY - 30, 0xAAAAAA);
            String qualityDescription = switch (draft.quality) {
                case STANDARD -> "20 FPS, textures up to 2048 px";
                case HIGH_FRAME_RATE -> "Up to 60 FPS, textures up to 2048 px";
                case HIGH_RESOLUTION -> "20 FPS, textures up to 4096 px";
                case HIGH_QUALITY -> "Up to 60 FPS, textures up to 4096 px";
            };
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(qualityDescription),
                    centerX, startY + 102, 0x888888);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
}
