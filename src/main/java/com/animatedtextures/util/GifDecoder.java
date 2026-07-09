package com.animatedtextures.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java GIF89a decoder supporting animated GIFs.
 * Decodes all frames and their inter-frame delays.
 *
 * Based on the public-domain GIF decoder algorithm.
 *
 * Note: The caller is responsible for closing the InputStream.
 */
public class GifDecoder {

    // --- Constants ---
    private static final int MAX_STACK_SIZE = 4096;

    /** Maximum GIF file size (50 MB) to prevent OOM from malicious resource packs. */
    private static final int MAX_FILE_SIZE = 50 * 1024 * 1024;

    /** Maximum iterations in the LZW decoder to prevent infinite loops on corrupt data. */
    private static final int MAX_LZW_ITERATIONS_MULTIPLIER = 3;

    // --- Fields ---
    private InputStream in;
    private int width, height;
    private boolean hasGlobalColorTable;
    private int bgIndex;
    private int loopCount = 1;

    private int[] globalColorTable;
    private int[] localColorTable;
    private int[] currentColorTable;

    // Per-frame fields
    private int frameX, frameY, frameWidth, frameHeight;
    private boolean interlace;
    private int dispose;
    private boolean transparency;
    private int transIndex;
    private int delay; // centiseconds

    private byte[] block = new byte[256];
    private int blockSize;

    private int[] image;       // current frame pixels (ARGB)
    private int[] lastImage;   // previous frame pixels

    private final List<AnimatedFrame> frames = new ArrayList<>();

    /**
     * Read all frames from the given GIF stream.
     * The stream is buffered into memory so we can enforce a size limit.
     * Caller retains responsibility for closing the original stream.
     *
     * @param stream the GIF data input stream
     * @return immutable list of decoded frames
     * @throws Exception if the data is corrupt, too large, or not a valid GIF
     */
    public List<AnimatedFrame> decode(InputStream stream) throws Exception {
        // Buffer the stream so we can check size and avoid streaming issues
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int total = 0;
        int n;
        while ((n = stream.read(tmp)) != -1) {
            total += n;
            if (total > MAX_FILE_SIZE) {
                throw new Exception("GIF file too large: exceeds " + MAX_FILE_SIZE + " bytes");
            }
            buffer.write(tmp, 0, n);
        }
        byte[] data = buffer.toByteArray();
        this.in = new java.io.ByteArrayInputStream(data);
        frames.clear();
        readHeader();
        readContents();
        return List.copyOf(frames);
    }

    // ---- Header ----

    private void readHeader() throws Exception {
        // Signature: GIF87a or GIF89a
        byte[] sig = readBytes(6);
        String header = new String(sig, 0, 3);
        if (!header.equals("GIF")) throw new Exception("Not a GIF file");

        // Logical screen descriptor
        width = readShort();
        height = readShort();
        int packed = read();
        hasGlobalColorTable = (packed & 0x80) != 0;
        int gctSize = 2 << (packed & 0x07);
        bgIndex = read();
        read(); // pixel aspect ratio (ignored)

        if (hasGlobalColorTable) {
            globalColorTable = readColorTable(gctSize);
        }
        currentColorTable = globalColorTable;

        image = new int[width * height];
        lastImage = new int[width * height];
    }

    // ---- Content blocks ----

    private void readContents() throws Exception {
        boolean done = false;
        while (!done) {
            int code = read();
            switch (code) {
                case 0x2C -> readImage();  // Image descriptor
                case 0x21 -> {             // Extension
                    int ext = read();
                    switch (ext) {
                        case 0xFF -> readApplicationExtension();
                        case 0xF9 -> readGraphicControlExtension();
                        default -> skip();
                    }
                }
                case 0x3B -> done = true;  // Trailer
                default -> {}
            }
        }
    }

    private void readGraphicControlExtension() throws Exception {
        read(); // block size (always 4)
        int packed = read();
        dispose = (packed & 0x1C) >> 2;
        transparency = (packed & 0x01) != 0;
        delay = readShort(); // centiseconds
        transIndex = read();
        read(); // block terminator
    }

    private void readApplicationExtension() throws Exception {
        readBlock();
        String app = new String(block, 0, Math.min(blockSize, 11));
        if ("NETSCAPE2.0".equals(app) || "ANIMEXTS1.0".equals(app)) {
            readBlock();
            if (blockSize >= 3 && block[0] == 0x01) {
                loopCount = (block[1] & 0xFF) | ((block[2] & 0xFF) << 8);
            }
        }
        skip();
    }

    private void readImage() throws Exception {
        frameX = readShort();
        frameY = readShort();
        frameWidth = readShort();
        frameHeight = readShort();
        int packed = read();
        boolean hasLocalCT = (packed & 0x80) != 0;
        interlace = (packed & 0x40) != 0;
        int lctSize = 2 << (packed & 0x07);

        if (hasLocalCT) {
            localColorTable = readColorTable(lctSize);
            currentColorTable = localColorTable;
        } else {
            currentColorTable = globalColorTable;
        }

        // Save previous frame for dispose=3 (restore to previous)
        System.arraycopy(image, 0, lastImage, 0, image.length);

        decodeImageData();
        bakeFrame();

        // Convert delay: GIF centiseconds → milliseconds
        int durationMs = delay * 10;
        if (durationMs == 0) durationMs = 100; // default 100ms for no-delay GIFs

        // Create ARGB copy for the frame
        int[] frameCopy = image.clone();
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, width, height, frameCopy, 0, width);
        frames.add(new AnimatedFrame(bi, durationMs));

        // Handle disposal
        if (dispose == 2) {
            // DISPOSE_OP_BACKGROUND: clear frame region to transparent (per GIF89a spec)
            fillRect(image, frameX, frameY, frameWidth, frameHeight, 0);
        } else if (dispose == 3) {
            System.arraycopy(lastImage, 0, image, 0, image.length);
        }
        dispose = 0;
        transparency = false;
        delay = 0;
    }

    private void bakeFrame() {
        // Apply current frame pixels into the composite image buffer
        // (already done by decodeImageData)
    }

    // ---- LZW Decoder ----

    private void decodeImageData() throws Exception {
        int minCodeSize = read();
        int clearCode = 1 << minCodeSize;
        int eofCode = clearCode + 1;

        short[] prefix = new short[MAX_STACK_SIZE];
        byte[] suffix = new byte[MAX_STACK_SIZE];
        byte[] pixelStack = new byte[MAX_STACK_SIZE + 1];

        int top = 0, first = 0, bi = 0, pi = 0;
        int codeSize = minCodeSize + 1;
        int codeMask = (1 << codeSize) - 1;
        int available = clearCode + 2;
        int oldCode = -1;

        for (int i = 0; i < clearCode; i++) {
            prefix[i] = 0;
            suffix[i] = (byte) i;
        }

        // Data sub-blocks
        int datum = 0, bits = 0, count = 0, inCode, code;
        byte[] data = new byte[256];
        int dataLen = 0;
        int dataPos = 0;

        int pixelCount = frameWidth * frameHeight;
        int[] line = interlace ? new int[]{0, 4, 2, 1} : new int[]{0};
        int[] lineInc = interlace ? new int[]{8, 8, 4, 2} : new int[]{1};
        int pass = 0;
        int ix = 0, iy = interlace ? line[0] : 0;
        int safetyCounter = 0;
        int maxIterations = pixelCount * MAX_LZW_ITERATIONS_MULTIPLIER;

        outer:
        for (int p = 0; p < pixelCount; ) {
            // Safety check: prevent infinite loops on corrupt GIF data
            if (++safetyCounter > maxIterations) {
                System.err.println("[AnimatedTextures] GIF LZW decoder exceeded safety limit, data may be corrupt.");
                break;
            }
            if (top == 0) {
                if (bits < codeSize) {
                    if (dataPos >= dataLen) {
                        dataLen = readBlock();
                        if (dataLen <= 0) break;
                        data = new byte[dataLen];
                        System.arraycopy(block, 0, data, 0, dataLen);
                        dataPos = 0;
                    }
                    datum += (data[dataPos++] & 0xFF) << bits;
                    bits += 8;
                    continue;
                }
                code = datum & codeMask;
                datum >>= codeSize;
                bits -= codeSize;

                if (code == clearCode) {
                    codeSize = minCodeSize + 1;
                    codeMask = (1 << codeSize) - 1;
                    available = clearCode + 2;
                    oldCode = -1;
                    continue;
                }
                if (code == eofCode || code > available) break;

                if (oldCode == -1) {
                    pixelStack[top++] = suffix[code];
                    oldCode = code;
                    first = code;
                    continue;
                }
                inCode = code;
                if (code >= available) {
                    pixelStack[top++] = (byte) first;
                    code = oldCode;
                }
                while (code >= clearCode) {
                    pixelStack[top++] = suffix[code];
                    code = prefix[code];
                }
                first = suffix[code] & 0xFF;
                pixelStack[top++] = (byte) first;

                if (available < MAX_STACK_SIZE) {
                    prefix[available] = (short) oldCode;
                    suffix[available] = (byte) first;
                    available++;
                    if (((available & codeMask) == 0) && (available < MAX_STACK_SIZE)) {
                        codeSize++;
                        codeMask = (1 << codeSize) - 1;
                    }
                }
                oldCode = inCode;
            }

            top--;
            int colorIndex = pixelStack[top] & 0xFF;
            int argb;
            if (transparency && colorIndex == transIndex) {
                argb = 0; // transparent
            } else {
                argb = (colorIndex < currentColorTable.length)
                        ? currentColorTable[colorIndex] : 0xFF000000;
                argb |= 0xFF000000; // ensure fully opaque
            }

            // Write pixel to composite image
            int px = frameX + ix;
            int py = frameY + iy;
            if (px < width && py < height) {
                image[py * width + px] = argb;
            }

            ix++;
            if (ix >= frameWidth) {
                ix = 0;
                if (interlace) {
                    iy += lineInc[pass];
                    while (pass < 3 && iy >= frameHeight) {
                        pass++;
                        iy = line[pass];
                    }
                } else {
                    iy++;
                }
            }
            p++;
        }

        // Drain remaining sub-blocks
        while (readBlock() > 0) {}
    }

    // ---- Color table ----

    private int[] readColorTable(int size) throws Exception {
        int count = size * 3;
        byte[] raw = readBytes(count);
        int[] ct = new int[size];
        for (int i = 0; i < size; i++) {
            int r = raw[i * 3] & 0xFF;
            int g = raw[i * 3 + 1] & 0xFF;
            int b = raw[i * 3 + 2] & 0xFF;
            ct[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return ct;
    }

    // ---- Utilities ----

    private void fillRect(int[] buf, int x, int y, int w, int h, int color) {
        for (int row = y; row < y + h && row < height; row++) {
            for (int col = x; col < x + w && col < width; col++) {
                buf[row * width + col] = color;
            }
        }
    }

    private int read() throws Exception {
        int b = in.read();
        if (b < 0) throw new Exception("Unexpected end of GIF stream");
        return b;
    }

    private int readShort() throws Exception {
        return read() | (read() << 8);
    }

    private byte[] readBytes(int n) throws Exception {
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r < 0) throw new Exception("Unexpected end of stream");
            read += r;
        }
        return buf;
    }

    private int readBlock() throws Exception {
        blockSize = read();
        if (blockSize == 0) return 0;
        block = readBytes(blockSize);
        return blockSize;
    }

    private void skip() throws Exception {
        while (readBlock() > 0) {}
    }
}
