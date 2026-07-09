package com.animatedtextures.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for Animated Textures mod.
 * Persisted as JSON in the game's config directory.
 */
public class AnimatedTexturesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("animated_textures.json");

    private static AnimatedTexturesConfig INSTANCE;

    // --- Config fields (serialized to JSON) ---

    /** Scaling algorithm for upscaling animated textures */
    public ScalingMode scalingMode = ScalingMode.BILINEAR;

    /** Whether to generate mipmaps for animated textures */
    public boolean enableMipmaps = true;

    /** Atlas size override. 0 = use Minecraft default (typically 1024).
     *  Note: Atlas size override mixin is currently disabled pending correct method mapping.
     *  Use high-resolution resource packs that set their own atlas size instead. */
    public int atlasSize = 0;

    /** Log level for animated texture operations (NONE, WARN, INFO, DEBUG) */
    public LogLevel logLevel = LogLevel.WARN;

    // --- Enums ---

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

    public enum LogLevel {
        NONE, WARN, INFO, DEBUG;

        public LogLevel next() {
            LogLevel[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    // --- Singleton access ---

    public static AnimatedTexturesConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static AnimatedTexturesConfig get() {
        return getInstance();
    }

    // --- Persistence ---

    private static AnimatedTexturesConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                AnimatedTexturesConfig config = GSON.fromJson(json, AnimatedTexturesConfig.class);
                AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Config loaded from {}", CONFIG_PATH);
                return config;
            } catch (Exception e) {
                AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] Failed to load config, using defaults: {}", e.getMessage());
            }
        }
        return new AnimatedTexturesConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
            AnimatedTexturesClient.LOGGER.debug("[AnimatedTextures] Config saved to {}", CONFIG_PATH);
        } catch (IOException e) {
            AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] Failed to save config: {}", e.getMessage());
        }
    }

    // --- Convenience methods ---

    /**
     * Returns true if bilinear interpolation should be used for upscaling.
     */
    public boolean shouldUseBilinear() {
        return scalingMode == ScalingMode.BILINEAR;
    }

    /**
     * Returns the effective atlas size. 0 means use Minecraft's default.
     */
    public int getEffectiveAtlasSize() {
        return atlasSize;
    }

    /**
     * Returns true if logging at the given level is enabled.
     */
    public boolean isLogEnabled(LogLevel level) {
        return logLevel.ordinal() >= level.ordinal();
    }
}
