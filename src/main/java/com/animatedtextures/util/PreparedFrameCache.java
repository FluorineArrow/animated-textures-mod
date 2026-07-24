package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesConfig;
import net.minecraft.client.texture.NativeImage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

final class PreparedFrameCache implements AutoCloseable {

    record Key(AnimatedTexture texture, int frameIndex, int width, int height, int mipLevel,
               AnimatedTexturesConfig.ScalingMode scalingMode) {
    }

    private final long maximumBytes;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long retainedBytes;

    PreparedFrameCache(long maximumBytes) {
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("Cache size must not be negative");
        }
        this.maximumBytes = maximumBytes;
    }

    boolean canCache(Key key) {
        long cycleBytes = saturatedMultiply(
                estimateMipChainBytes(key.width(), key.height(), key.mipLevel()),
                key.texture().getFrameCount());
        return cycleBytes <= maximumBytes;
    }

    NativeImage[] getOrCreate(Key key, Supplier<NativeImage[]> factory) {
        Entry existing = entries.get(key);
        if (existing != null) {
            return existing.images;
        }
        NativeImage[] images = factory.get();
        long bytes = imageBytes(images);
        while (!entries.isEmpty() && retainedBytes > maximumBytes - bytes) {
            Map.Entry<Key, Entry> eldest = entries.entrySet().iterator().next();
            retainedBytes -= eldest.getValue().bytes;
            closeImages(eldest.getValue().images);
            entries.remove(eldest.getKey());
        }
        if (bytes > maximumBytes) {
            closeImages(images);
            throw new IllegalStateException("Prepared frame exceeds its cache budget");
        }
        entries.put(key, new Entry(images, bytes));
        retainedBytes += bytes;
        return images;
    }

    static long estimateMipChainBytes(int width, int height, int mipLevel) {
        if (width <= 0 || height <= 0 || mipLevel < 0) {
            throw new IllegalArgumentException("Invalid prepared-frame dimensions or mip level");
        }
        long bytes = 0;
        for (int level = 0; level <= mipLevel; level++) {
            long levelBytes = (long) Math.max(1, width >> level)
                    * Math.max(1, height >> level) * Integer.BYTES;
            bytes = bytes > Long.MAX_VALUE - levelBytes ? Long.MAX_VALUE : bytes + levelBytes;
        }
        return bytes;
    }

    @Override
    public void close() {
        for (Entry entry : entries.values()) {
            closeImages(entry.images);
        }
        entries.clear();
        retainedBytes = 0;
    }

    static void closeImages(NativeImage[] images) {
        Set<NativeImage> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(unique, images);
        for (NativeImage image : unique) {
            image.close();
        }
    }

    private static long imageBytes(NativeImage[] images) {
        long bytes = 0;
        Set<NativeImage> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(unique, images);
        for (NativeImage image : unique) {
            long imageBytes = (long) image.getWidth() * image.getHeight() * image.getFormat().getChannelCount();
            bytes = bytes > Long.MAX_VALUE - imageBytes ? Long.MAX_VALUE : bytes + imageBytes;
        }
        return bytes;
    }

    private static long saturatedMultiply(long left, long right) {
        return left == 0 || right <= Long.MAX_VALUE / left ? left * right : Long.MAX_VALUE;
    }

    private record Entry(NativeImage[] images, long bytes) {
    }
}
