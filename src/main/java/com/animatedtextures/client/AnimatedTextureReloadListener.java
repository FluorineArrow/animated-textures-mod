package com.animatedtextures.client;

import com.animatedtextures.util.AnimatedTextureRegistry;
import com.animatedtextures.util.AnimatedTextureTickManager;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePack;

public class AnimatedTextureReloadListener implements SimpleSynchronousResourceReloadListener {

    private static final Identifier ID = Identifier.of("animated_textures", "reload_listener");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Scanning resource packs for .gif and .png3 files...");
        cachedPacks = null; // reset pack cache for this reload
        AnimatedTextureRegistry.INSTANCE.clear();
        AnimatedTextureTickManager.reset();
        AnimatedTextureTickManager.register();

        int gifCount = 0;
        int apngCount = 0;

        // Minecraft's ResourceManager won't index unknown extensions like .gif/.png3.
        // Instead, we iterate every active resource pack and walk its namespaces,
        // then probe for .gif/.png3 by trying to open known-path variants of every
        // .png resource we find.
        //
        // Strategy:
        //   1. findResources("textures", *.png) to get all texture identifiers
        //   2. For each, try opening <same-path>.gif and <same-path>.png3 from the raw packs
        //   3. Also brute-scan pack roots for any .gif/.png3 not paired with a .png

        // Step 1: collect all known .png texture paths as candidates
        var pngResources = manager.findResources("textures", id -> id.getPath().endsWith(".png"));

        List<Identifier> candidates = new ArrayList<>();
        for (Identifier pngId : pngResources.keySet()) {
            // Strip .png to get the base path, e.g. "textures/block/gold_ore"
            String basePath = pngId.getPath().substring(0, pngId.getPath().length() - 4);
            String ns = pngId.getNamespace();
            candidates.add(Identifier.of(ns, basePath));
        }

        // Step 2: for each candidate base path, try .gif and .png3
        java.util.Set<Identifier> loadedIds = new java.util.HashSet<>();
        for (Identifier base : candidates) {
            // Try GIF
            Identifier gifId = Identifier.of(base.getNamespace(), base.getPath() + ".gif");
            InputStream gifStream = openResourceOrFallback(manager, gifId);
            if (gifStream != null) {
                try (InputStream is = gifStream) {
                    AnimatedTextureRegistry.INSTANCE.registerGif(gifId, is);
                    gifCount++;
                    loadedIds.add(gifId);
                    AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Loaded GIF: {}", gifId);
                } catch (Exception e) {
                    AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] Failed GIF {}: {}", gifId, e.getMessage());
                }
            }

            // Try APNG (.png3)
            Identifier apngId = Identifier.of(base.getNamespace(), base.getPath() + ".png3");
            InputStream apngStream = openResourceOrFallback(manager, apngId);
            if (apngStream != null) {
                try (InputStream is = apngStream) {
                    AnimatedTextureRegistry.INSTANCE.registerApng(apngId, is);
                    apngCount++;
                    loadedIds.add(apngId);
                    AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Loaded APNG: {}", apngId);
                } catch (Exception e) {
                    AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] Failed APNG {}: {}", apngId, e.getMessage());
                }
            }
        }

        // Step 3: scan for standalone .gif/.png3 files that weren't already loaded in step 2.
        // findResources discovers ALL files with these extensions, including those already
        // loaded as paired files. The loadedIds set prevents duplicate loading.
        var gifResources = manager.findResources("textures", id -> id.getPath().endsWith(".gif"));
        for (Identifier gifId : gifResources.keySet()) {
            if (loadedIds.contains(gifId)) continue; // already loaded in step 2
            InputStream is = openResourceOrFallback(manager, gifId);
            if (is == null) continue;
            try (InputStream stream = is) {
                AnimatedTextureRegistry.INSTANCE.registerGif(gifId, stream);
                gifCount++;
                loadedIds.add(gifId);
                AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Loaded standalone GIF: {}", gifId);
            } catch (Exception e) {
                AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] Failed GIF {}: {}", gifId, e.getMessage());
            }
        }
        var png3Resources = manager.findResources("textures", id -> id.getPath().endsWith(".png3"));
        for (Identifier png3Id : png3Resources.keySet()) {
            if (loadedIds.contains(png3Id)) continue; // already loaded in step 2
            InputStream is = openResourceOrFallback(manager, png3Id);
            if (is == null) continue;
            try (InputStream stream = is) {
                AnimatedTextureRegistry.INSTANCE.registerApng(png3Id, stream);
                apngCount++;
                loadedIds.add(png3Id);
                AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Loaded standalone APNG: {}", png3Id);
            } catch (Exception e) {
                AnimatedTexturesClient.LOGGER.warn("[AnimatedTextures] Failed APNG {}: {}", png3Id, e.getMessage());
            }
        }

        AnimatedTexturesClient.LOGGER.info("[AnimatedTextures] Done. Loaded {} GIF(s) and {} APNG(s).", gifCount, apngCount);

        // Release cached pack references
        if (cachedPacks != null) {
            for (ResourcePack pack : cachedPacks) {
                try { pack.close(); } catch (Exception ignored) {}
            }
            cachedPacks = null;
        }

        // Trigger sprite registration now that the registry is populated.
        // If the atlas was already built (normal case), this scans it immediately.
        // If the atlas hasn't been built yet (first load), the tick manager will
        // scan it on the first tick after upload() fires.
        AnimatedTextureTickManager.onRegistryReady();
    }

    /** Cached pack list for the duration of a single reload. */
    private List<ResourcePack> cachedPacks;

    /**
     * Open a resource by identifier, falling back to direct pack access if
     * ResourceManager.getResource() returns empty (which happens for non-standard
     * extensions like .gif/.png3 in some Minecraft versions).
     *
     * @return an InputStream, or null if the resource doesn't exist
     */
    private InputStream openResourceOrFallback(ResourceManager manager, Identifier id) {
        // Try the standard ResourceManager lookup first
        Optional<net.minecraft.resource.Resource> res = manager.getResource(id);
        if (res.isPresent()) {
            try {
                return res.get().getInputStream();
            } catch (Exception ignored) {
                // Fall through to direct pack access
            }
        }

        // Fallback: open directly from resource packs.
        // getResource() may return empty for non-standard extensions (.gif, .png3),
        // but the files exist on disk and can be read via ResourcePack.open().
        if (cachedPacks == null) {
            ResourcePackManager packManager = net.minecraft.client.MinecraftClient.getInstance()
                    .getResourcePackManager();
            cachedPacks = packManager.createResourcePacks();
        }
        for (ResourcePack pack : cachedPacks) {
            try {
                net.minecraft.resource.InputSupplier<InputStream> supplier =
                        pack.open(net.minecraft.resource.ResourceType.CLIENT_RESOURCES, id);
                if (supplier != null) {
                    InputStream is = supplier.get();
                    if (is != null) {
                        AnimatedTexturesClient.LOGGER.info(
                                "[AnimatedTextures] Opened {} directly from pack '{}'", id, pack.getInfo().id());
                        return is;
                    }
                }
            } catch (Exception ignored) {
                // Pack doesn't have this file, try next
            }
        }
        return null;
    }
}
