package com.animatedtextures.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import com.animatedtextures.util.AnimatedTextureRegistry;
import com.animatedtextures.client.AnimatedTexturesConfig.ScalingMode;
import com.animatedtextures.client.AnimatedTexturesConfig.LogLevel;

/**
 * ModMenu integration for Animated Textures.
 * Provides a config screen accessible from the Mods menu.
 */
public class AnimatedTexturesModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AnimatedTexturesConfigScreen::new;
    }

    /**
     * Config screen with controls for scaling mode, atlas size, and other options.
     */
    private static class AnimatedTexturesConfigScreen extends Screen {

        private final Screen parent;
        private final AnimatedTexturesConfig config;

        protected AnimatedTexturesConfigScreen(Screen parent) {
            super(Text.literal("Animated Textures"));
            this.parent = parent;
            this.config = AnimatedTexturesConfig.get();
        }

        @Override
        protected void init() {
            int centerX = width / 2;
            int startY = height / 2 - 80;
            int buttonWidth = 200;
            int buttonHeight = 20;
            int spacing = 24;

            // Title
            addDrawableChild(
                    ButtonWidget.builder(Text.literal("Animated Textures - Settings"),
                                    btn -> {})
                            .dimensions(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
                            .build()
            );

            // Loaded textures count
            int count = AnimatedTextureRegistry.INSTANCE.size();
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Loaded: " + count + " animated texture(s)"),
                                    btn -> {})
                            .dimensions(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight)
                            .build()
            );

            // Scaling Mode toggle
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Scaling: " + config.scalingMode.getDisplayName()),
                                    btn -> {
                                        config.scalingMode = config.scalingMode.next();
                                        btn.setMessage(Text.literal("Scaling: " + config.scalingMode.getDisplayName()));
                                    })
                            .dimensions(centerX - buttonWidth / 2, startY + spacing * 2 + 4, buttonWidth, buttonHeight)
                            .build()
            );

            // Atlas Size toggle (cycle through: Default, 2048, 4096)
            String atlasLabel = config.atlasSize <= 0 ? "Default" : config.atlasSize + "x" + config.atlasSize;
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Atlas Size: " + atlasLabel),
                                    btn -> {
                                        // Cycle: 0 (default) -> 2048 -> 4096 -> 0
                                        if (config.atlasSize <= 0) {
                                            config.atlasSize = 2048;
                                        } else if (config.atlasSize == 2048) {
                                            config.atlasSize = 4096;
                                        } else {
                                            config.atlasSize = 0;
                                        }
                                        String label = config.atlasSize <= 0 ? "Default" : config.atlasSize + "x" + config.atlasSize;
                                        btn.setMessage(Text.literal("Atlas Size: " + label));
                                    })
                            .dimensions(centerX - buttonWidth / 2, startY + spacing * 3 + 4, buttonWidth, buttonHeight)
                            .build()
            );

            // Log Level toggle
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Log Level: " + config.logLevel.name()),
                                    btn -> {
                                        config.logLevel = config.logLevel.next();
                                        btn.setMessage(Text.literal("Log Level: " + config.logLevel.name()));
                                    })
                            .dimensions(centerX - buttonWidth / 2, startY + spacing * 4 + 4, buttonWidth, buttonHeight)
                            .build()
            );

            // Supported formats info
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Formats: .gif (GIF89a) and .png3 (APNG)"),
                                    btn -> {})
                            .dimensions(centerX - buttonWidth / 2, startY + spacing * 5 + 8, buttonWidth, buttonHeight)
                            .build()
            );

            // Save & Done button
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Save & Done"),
                                    btn -> {
                                        config.save();
                                        close();
                                    })
                            .dimensions(centerX - 50, startY + spacing * 6 + 12, 100, buttonHeight)
                            .build()
            );
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);

            int centerX = width / 2;
            int startY = height / 2 - 80;

            // Draw description text
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Place .gif or .png3 files in your resource pack's"),
                    centerX, startY - 30, 0xAAAAAA);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("assets/<namespace>/textures/ folder to use them."),
                    centerX, startY - 18, 0xAAAAAA);

            // Draw scaling mode description
            String scalingDesc = config.scalingMode == ScalingMode.BILINEAR
                    ? "Smooth upscaling for high-res textures"
                    : "Fast pixel-perfect scaling";
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(scalingDesc),
                    centerX, startY + 24 * 2 + 24 + 2, 0x888888);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
}
