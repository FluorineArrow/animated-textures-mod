package com.animatedtextures.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Java GIF87a/GIF89a decoder that composites all animation frames.
 * The caller remains responsible for closing the supplied stream.
 */
public class GifDecoder {

    private static final int MAX_STACK_SIZE = 4096;

    private final AnimatedImageLimits limits;
    private final List<AnimatedFrame> frames = new ArrayList<>();

    private InputStream in;
    private int width;
    private int height;
    private int[] globalColorTable;
    private int[] currentColorTable;
    private int backgroundColor;
    private int[] image;
    private int[] previousImage;
    private long retainedPixels;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;
    private boolean interlace;
    private int dispose;
    private boolean transparency;
    private int transparentIndex;
    private int delayCentiseconds;
    private long totalPlays;

    GifDecoder(AnimatedImageLimits limits) {
        this.limits = limits;
    }

    public GifDecoder(AnimatedTextureReloadBudget.Remaining remaining) {
        this(AnimatedImageLimits.DEFAULT.forRemaining(remaining));
    }

    public GifDecoder() {
        this(AnimatedImageLimits.DEFAULT);
    }

    /**
     * Decodes frames only. Use {@link #decodeAnimation(InputStream)} to preserve playback metadata.
     */
    @Deprecated
    public List<AnimatedFrame> decode(InputStream stream) throws Exception {
        return decodeAnimation(stream).frames();
    }

    public DecodedAnimation decodeAnimation(InputStream stream) throws Exception {
        resetState(new ByteArrayInputStream(limits.readBounded(stream, "GIF")));
        readHeader();
        readContents();
        if (frames.isEmpty()) {
            throw new IOException("GIF contains no image frames");
        }
        return new DecodedAnimation(frames, totalPlays);
    }

    private void resetState(InputStream stream) {
        in = stream;
        frames.clear();
        width = 0;
        height = 0;
        globalColorTable = null;
        currentColorTable = null;
        backgroundColor = 0;
        image = null;
        previousImage = null;
        retainedPixels = 0;
        frameX = 0;
        frameY = 0;
        frameWidth = 0;
        frameHeight = 0;
        interlace = false;
        totalPlays = 1;
        resetGraphicControl();
    }

    private void readHeader() throws Exception {
        String signature = new String(readBytes(6), StandardCharsets.US_ASCII);
        if (!"GIF87a".equals(signature) && !"GIF89a".equals(signature)) {
            throw new IOException("Not a GIF87a or GIF89a file");
        }

        width = readShort();
        height = readShort();
        int packed = read();
        boolean hasGlobalColorTable = (packed & 0x80) != 0;
        int globalColorTableSize = 2 << (packed & 0x07);
        int backgroundIndex = read();
        read();

        int pixelCount = limits.checkedPixels(width, height, "GIF logical screen");
        if (hasGlobalColorTable) {
            globalColorTable = readColorTable(globalColorTableSize);
            if (backgroundIndex >= globalColorTable.length) {
                throw new IOException("GIF logical-screen background index is outside the global color table");
            }
            backgroundColor = globalColorTable[backgroundIndex];
        } else {
            backgroundColor = 0;
        }
        currentColorTable = globalColorTable;
        image = new int[pixelCount];
        Arrays.fill(image, backgroundColor);
    }

    private void readContents() throws Exception {
        while (true) {
            switch (read()) {
                case 0x2C -> readImage();
                case 0x21 -> readExtension();
                case 0x3B -> {
                    return;
                }
                default -> throw new IOException("Unexpected GIF block introducer");
            }
        }
    }

    private void readExtension() throws Exception {
        switch (read()) {
            case 0xF9 -> readGraphicControlExtension();
            case 0xFF -> readApplicationExtension();
            case 0x01 -> {
                skipSubBlocks();
                resetGraphicControl();
            }
            default -> skipSubBlocks();
        }
    }

    private void readGraphicControlExtension() throws Exception {
        if (read() != 4) {
            throw new IOException("GIF graphic control extension must contain four bytes");
        }
        int packed = read();
        dispose = (packed & 0x1C) >>> 2;
        if (dispose > 3) {
            throw new IOException("GIF uses an unsupported disposal method");
        }
        transparency = (packed & 1) != 0;
        delayCentiseconds = readShort();
        transparentIndex = read();
        if (read() != 0) {
            throw new IOException("GIF graphic control extension is missing its terminator");
        }
    }

    private void readApplicationExtension() throws Exception {
        byte[] identifier = readSubBlock();
        if (identifier.length == 0) {
            throw new IOException("GIF application extension is missing its identifier");
        }
        boolean recognized = identifier.length == 11
                && (Arrays.equals(identifier, "NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII))
                || Arrays.equals(identifier, "ANIMEXTS1.0".getBytes(StandardCharsets.US_ASCII)));
        byte[] block;
        boolean parsedLoop = false;
        while ((block = readSubBlock()).length != 0) {
            if (recognized && !parsedLoop && block.length == 3 && block[0] == 1) {
                int repetitions = (block[1] & 0xFF) | (block[2] & 0xFF) << 8;
                totalPlays = repetitions == 0 ? DecodedAnimation.INFINITE_PLAYS : (long) repetitions + 1;
                parsedLoop = true;
            }
        }
        if (recognized && !parsedLoop) {
            throw new IOException("GIF loop application extension has no valid loop payload");
        }
    }

    private void readImage() throws Exception {
        frameX = readShort();
        frameY = readShort();
        frameWidth = readShort();
        frameHeight = readShort();
        validateFrameRectangle();

        int packed = read();
        boolean hasLocalColorTable = (packed & 0x80) != 0;
        interlace = (packed & 0x40) != 0;
        if (hasLocalColorTable) {
            currentColorTable = readColorTable(2 << (packed & 0x07));
        } else {
            currentColorTable = globalColorTable;
        }
        if (currentColorTable == null) {
            throw new IOException("GIF image frame has no color table");
        }

        int canvasPixels = limits.checkedPixels(width, height, "GIF frame canvas");
        limits.reserveFrame(frames.size(), retainedPixels, canvasPixels, "GIF");
        if (dispose == 3) {
            previousImage = image.clone();
        }

        decodeImageData();

        int durationMs = delayCentiseconds == 0 ? 100 : delayCentiseconds * 10;
        frames.add(new AnimatedFrame(image, width, height, durationMs));
        retainedPixels += canvasPixels;

        if (dispose == 2) {
            fillRect(image, frameX, frameY, frameWidth, frameHeight, backgroundColor);
        } else if (dispose == 3) {
            System.arraycopy(previousImage, 0, image, 0, image.length);
            previousImage = null;
        }
        resetGraphicControl();
    }

    private void validateFrameRectangle() throws IOException {
        limits.checkedPixels(frameWidth, frameHeight, "GIF image frame");
        long right = (long) frameX + frameWidth;
        long bottom = (long) frameY + frameHeight;
        if (right > width || bottom > height) {
            throw new IOException("GIF image frame lies outside the logical screen");
        }
    }

    private void decodeImageData() throws Exception {
        int minimumCodeSize = read();
        if (minimumCodeSize < 2 || minimumCodeSize > 8) {
            throw new IOException("GIF LZW minimum code size must be between 2 and 8");
        }

        int clearCode = 1 << minimumCodeSize;
        int endCode = clearCode + 1;
        short[] prefix = new short[MAX_STACK_SIZE];
        byte[] suffix = new byte[MAX_STACK_SIZE];
        byte[] pixelStack = new byte[MAX_STACK_SIZE + 1];
        for (int index = 0; index < clearCode; index++) {
            suffix[index] = (byte) index;
        }

        int available = clearCode + 2;
        int codeSize = minimumCodeSize + 1;
        int codeMask = (1 << codeSize) - 1;
        int oldCode = -1;
        int first = 0;
        int stackTop = 0;
        int datum = 0;
        int bits = 0;
        byte[] data = new byte[0];
        int dataOffset = 0;

        int[] interlaceStarts = {0, 4, 2, 1};
        int[] interlaceSteps = {8, 8, 4, 2};
        int pass = 0;
        int x = 0;
        int y = interlace ? interlaceStarts[0] : 0;
        int expectedPixels = frameWidth * frameHeight;
        int writtenPixels = 0;
        long decodedCodes = 0;
        long maximumCodes = Math.max(1L, (long) in.available() * 4);

        while (writtenPixels < expectedPixels) {
            if (stackTop == 0) {
                while (bits < codeSize) {
                    if (dataOffset >= data.length) {
                        data = readSubBlock();
                        dataOffset = 0;
                        if (data.length == 0) {
                            throw new IOException("GIF image data ended before its frame was complete");
                        }
                    }
                    datum |= (data[dataOffset++] & 0xFF) << bits;
                    bits += 8;
                }

                int code = datum & codeMask;
                if (++decodedCodes > maximumCodes) {
                    throw new IOException("GIF LZW data exceeds the encoded-data safety limit");
                }
                datum >>>= codeSize;
                bits -= codeSize;
                if (code == clearCode) {
                    available = clearCode + 2;
                    codeSize = minimumCodeSize + 1;
                    codeMask = (1 << codeSize) - 1;
                    oldCode = -1;
                    continue;
                }
                if (code == endCode) {
                    throw new IOException("GIF image data ended before its frame was complete");
                }
                if (code > available) {
                    throw new IOException("GIF LZW code exceeds the current dictionary");
                }
                if (oldCode == -1) {
                    if (code >= clearCode) {
                        throw new IOException("GIF LZW stream starts with an invalid code");
                    }
                    pixelStack[stackTop++] = suffix[code];
                    oldCode = code;
                    first = code;
                    continue;
                }

                int inputCode = code;
                if (code == available) {
                    if (stackTop >= pixelStack.length) {
                        throw new IOException("GIF LZW pixel stack overflow");
                    }
                    pixelStack[stackTop++] = (byte) first;
                    code = oldCode;
                }
                while (code >= clearCode) {
                    if (code >= available || stackTop >= pixelStack.length) {
                        throw new IOException("GIF LZW dictionary is malformed");
                    }
                    pixelStack[stackTop++] = suffix[code];
                    code = prefix[code];
                }
                first = suffix[code] & 0xFF;
                if (stackTop >= pixelStack.length) {
                    throw new IOException("GIF LZW pixel stack overflow");
                }
                pixelStack[stackTop++] = (byte) first;

                if (available < MAX_STACK_SIZE) {
                    prefix[available] = (short) oldCode;
                    suffix[available] = (byte) first;
                    available++;
                    if (available == (1 << codeSize) && available < MAX_STACK_SIZE) {
                        codeSize++;
                        codeMask = (1 << codeSize) - 1;
                    }
                }
                oldCode = inputCode;
            }

            int colorIndex = pixelStack[--stackTop] & 0xFF;
            if (!(transparency && colorIndex == transparentIndex)) {
                if (colorIndex >= currentColorTable.length) {
                    throw new IOException("GIF color index is outside its color table");
                }
                image[(frameY + y) * width + frameX + x] = currentColorTable[colorIndex];
            }

            x++;
            if (x == frameWidth) {
                x = 0;
                if (interlace) {
                    y += interlaceSteps[pass];
                    while (y >= frameHeight && pass < 3) {
                        pass++;
                        y = interlaceStarts[pass];
                    }
                } else {
                    y++;
                }
            }
            writtenPixels++;
        }

        while (readSubBlock().length != 0) {
            // Consume remaining image data before the next GIF block.
        }
    }

    private int[] readColorTable(int size) throws Exception {
        byte[] raw = readBytes(size * 3);
        int[] colors = new int[size];
        for (int index = 0; index < size; index++) {
            int red = raw[index * 3] & 0xFF;
            int green = raw[index * 3 + 1] & 0xFF;
            int blue = raw[index * 3 + 2] & 0xFF;
            colors[index] = 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
        return colors;
    }

    private void resetGraphicControl() {
        dispose = 0;
        transparency = false;
        transparentIndex = 0;
        delayCentiseconds = 0;
    }

    private void fillRect(int[] pixels, int x, int y, int rectangleWidth, int rectangleHeight, int color) {
        for (int row = y; row < y + rectangleHeight; row++) {
            for (int column = x; column < x + rectangleWidth; column++) {
                pixels[row * width + column] = color;
            }
        }
    }

    private int read() throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new IOException("Unexpected end of GIF stream");
        }
        return value;
    }

    private int readShort() throws IOException {
        return read() | read() << 8;
    }

    private byte[] readBytes(int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new IOException("Unexpected end of GIF stream");
            }
            offset += read;
        }
        return bytes;
    }

    private byte[] readSubBlock() throws IOException {
        return readBytes(read());
    }

    private void skipSubBlocks() throws IOException {
        while (readSubBlock().length != 0) {
            // Skip an unneeded extension payload.
        }
    }
}
