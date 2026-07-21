package com.animatedtextures.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Supported persisted settings for Animated Textures.
 */
public final class AnimatedTexturesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AnimatedTexturesConfig instance;

    /** Scaling algorithm for generated animation frames. */
    public ScalingMode scalingMode = ScalingMode.BILINEAR;

    public enum ScalingMode {
        NEAREST("Nearest Neighbor (Fast)"),
        BILINEAR("Bilinear (Smooth)");

        private final String displayName;

        ScalingMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ScalingMode next() {
            ScalingMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static AnimatedTexturesConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    static AnimatedTexturesConfig parse(String json) {
        try {
            return sanitize(GSON.fromJson(json, AnimatedTexturesConfig.class));
        } catch (Exception exception) {
            return new AnimatedTexturesConfig();
        }
    }

    static AnimatedTexturesConfig sanitize(AnimatedTexturesConfig config) {
        if (config == null) {
            return new AnimatedTexturesConfig();
        }
        if (config.scalingMode == null) {
            config.scalingMode = ScalingMode.BILINEAR;
        }
        return config;
    }

    public AnimatedTexturesConfig copy() {
        AnimatedTexturesConfig copy = new AnimatedTexturesConfig();
        copy.scalingMode = scalingMode;
        return copy;
    }

    public static void replaceAndSave(AnimatedTexturesConfig config) {
        instance = sanitize(config);
        instance.save();
    }

    private static AnimatedTexturesConfig load() {
        Path configPath = configPath();
        if (!Files.exists(configPath)) {
            return new AnimatedTexturesConfig();
        }
        try {
            AnimatedTexturesConfig loaded = sanitize(GSON.fromJson(Files.readString(configPath), AnimatedTexturesConfig.class));
            AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Config loaded from {}", configPath);
            return loaded;
        } catch (Exception exception) {
            AnimatedTexturesClient.LOGGER.warn(
                    "[AnimatedTextures] repair category=config action=defaulted reason={}", exception.getMessage());
            return new AnimatedTexturesConfig();
        }
    }

    private void save() {
        Path configPath = configPath();
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this));
            AnimatedTexturesClient.LOGGER.debug("[AnimatedTextures] Config saved to {}", configPath);
        } catch (IOException exception) {
            AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] Failed to save config: {}", exception.getMessage());
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("animated_textures.json");
    }

    public boolean shouldUseBilinear() {
        return scalingMode == ScalingMode.BILINEAR;
    }
}
