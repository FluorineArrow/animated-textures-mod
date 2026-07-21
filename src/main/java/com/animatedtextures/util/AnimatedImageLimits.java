package com.animatedtextures.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class AnimatedImageLimits {

    static final AnimatedImageLimits DEFAULT = new AnimatedImageLimits(
            16 * 1024 * 1024,
            2_048,
            4_194_304,
            256,
            16_777_216,
            64 * 1024
    );

    final int maxEncodedBytes;
    final int maxDimension;
    final int maxFramePixels;
    final int maxFrames;
    final int maxTotalPixels;
    final int maxAncillaryBytes;

    AnimatedImageLimits(int maxEncodedBytes, int maxDimension, int maxFramePixels,
                        int maxFrames, int maxTotalPixels, int maxAncillaryBytes) {
        this.maxEncodedBytes = maxEncodedBytes;
        this.maxDimension = maxDimension;
        this.maxFramePixels = maxFramePixels;
        this.maxFrames = maxFrames;
        this.maxTotalPixels = maxTotalPixels;
        this.maxAncillaryBytes = maxAncillaryBytes;
    }

    AnimatedImageLimits forRemaining(AnimatedTextureReloadBudget.Remaining remaining) {
        if (!remaining.canDecode()) {
            throw new IllegalStateException("Reload budget has no remaining animation capacity");
        }
        int remainingPixels = (int) Math.min(Integer.MAX_VALUE, remaining.effectivePixels());
        return new AnimatedImageLimits(maxEncodedBytes, maxDimension, maxFramePixels,
                Math.min(maxFrames, remaining.frames()), Math.min(maxTotalPixels, remainingPixels),
                maxAncillaryBytes);
    }

    byte[] readBounded(InputStream stream, String format) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8_192];
        int total = 0;
        int read;
        while ((read = stream.read(chunk)) != -1) {
            if (read > maxEncodedBytes - total) {
                throw new IOException(format + " exceeds the encoded size limit of " + maxEncodedBytes + " bytes");
            }
            buffer.write(chunk, 0, read);
            total += read;
        }
        return buffer.toByteArray();
    }

    int checkedPixels(int width, int height, String description) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IOException(description + " dimensions must be positive");
        }
        if (width > maxDimension || height > maxDimension) {
            throw new IOException(description + " dimensions exceed " + maxDimension + " pixels per side");
        }
        long pixels = (long) width * height;
        if (pixels > maxFramePixels) {
            throw new IOException(description + " exceeds the frame pixel limit of " + maxFramePixels);
        }
        return (int) pixels;
    }

    void reserveFrame(int currentFrameCount, long retainedPixels, int pixels, String format) throws IOException {
        if (currentFrameCount >= maxFrames) {
            throw new IOException(format + " exceeds the frame limit of " + maxFrames);
        }
        long nextTotal = retainedPixels + pixels;
        if (nextTotal > maxTotalPixels) {
            throw new IOException(format + " exceeds the retained pixel limit of " + maxTotalPixels);
        }
    }
}
