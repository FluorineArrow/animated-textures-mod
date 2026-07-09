package com.animatedtextures.client;

import com.animatedtextures.util.AnimatedTextureRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnimatedTexturesClient implements ClientModInitializer {

    public static final String MOD_ID = "animated_textures";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[AnimatedTextures] Initializing APNG & GIF texture support...");

        // Register our resource reload listener so we can scan resource packs
        // for .png3 (APNG) and .gif files on every resource reload
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new AnimatedTextureReloadListener());

        LOGGER.info("[AnimatedTextures] Ready! Place .png3 or .gif files in your resource pack's textures folder.");
    }
}
