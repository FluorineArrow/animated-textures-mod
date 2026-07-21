package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GifDecoderTest {

    @Test
    void transparentPixelsPreserveTheCompositedCanvas() throws Exception {
        byte[] gif = {
                'G', 'I', 'F', '8', '9', 'a',
                2, 0, 1, 0, (byte) 0x80, 0, 0,
                (byte) 0xFF, 0, 0, 0, 0, (byte) 0xFF,
                0x2C, 0, 0, 0, 0, 2, 0, 1, 0, 0,
                2, 2, 0x44, 0x0A, 0,
                0x21, (byte) 0xF9, 4, 1, 1, 0, 1, 0,
                0x2C, 0, 0, 0, 0, 2, 0, 1, 0, 0,
                2, 2, 0x0C, 0x0A, 0,
                0x3B
        };

        var frames = new GifDecoder().decode(new ByteArrayInputStream(gif));

        assertArrayEquals(new int[]{0xFFFF0000, 0xFF0000FF}, frames.get(0).getPixels());
        assertArrayEquals(new int[]{0xFFFF0000, 0xFFFF0000}, frames.get(1).getPixels());
    }

    @Test
    void preservesGifLoopMetadata() throws Exception {
        assertEquals(1, new GifDecoder().decodeAnimation(
                new ByteArrayInputStream(singlePixelGif(true))).totalPlays());
        assertEquals(DecodedAnimation.INFINITE_PLAYS, new GifDecoder().decodeAnimation(
                new ByteArrayInputStream(withLoopExtension(singlePixelGif(true), "NETSCAPE2.0", 0))).totalPlays());
        assertEquals(3, new GifDecoder().decodeAnimation(
                new ByteArrayInputStream(withLoopExtension(singlePixelGif(true), "NETSCAPE2.0", 2))).totalPlays());
        assertEquals(4, new GifDecoder().decodeAnimation(
                new ByteArrayInputStream(withLoopExtension(singlePixelGif(true), "ANIMEXTS1.0", 3))).totalPlays());
    }

    @Test
    void repeatedGifLoopExtensionUsesTheLastValidValue() throws Exception {
        byte[] gif = withLoopExtension(singlePixelGif(true), "NETSCAPE2.0", 1);
        gif = withLoopExtension(gif, "NETSCAPE2.0", 4);

        assertEquals(5, new GifDecoder().decodeAnimation(new ByteArrayInputStream(gif)).totalPlays());
    }

    @Test
    void decoderReuseResetsLoopMetadata() throws Exception {
        GifDecoder decoder = new GifDecoder();
        decoder.decodeAnimation(new ByteArrayInputStream(
                withLoopExtension(singlePixelGif(true), "NETSCAPE2.0", 0)));

        assertEquals(1, decoder.decodeAnimation(new ByteArrayInputStream(singlePixelGif(true))).totalPlays());
    }

    @Test
    void decoderReuseDoesNotLeakThePreviousColorTable() throws Exception {
        GifDecoder decoder = new GifDecoder();

        decoder.decode(new ByteArrayInputStream(singlePixelGif(true)));

        assertThrows(IOException.class, () -> decoder.decode(new ByteArrayInputStream(singlePixelGif(false))));
    }

    @Test
    void failedDecodeDoesNotContaminateTheNextDecode() throws Exception {
        GifDecoder decoder = new GifDecoder();
        assertThrows(IOException.class, () -> decoder.decode(new ByteArrayInputStream(singlePixelGif(false))));

        var frames = decoder.decode(new ByteArrayInputStream(singlePixelGif(true)));

        assertArrayEquals(new int[]{0xFFFF0000}, frames.get(0).getPixels());
    }

    @Test
    void rejectsOversizedLogicalScreensBeforeAllocation() {
        byte[] header = {
                'G', 'I', 'F', '8', '9', 'a',
                0, 16, 0, 16, 0, 0, 0,
                0x3B
        };

        assertThrows(IOException.class, () -> new GifDecoder().decode(new ByteArrayInputStream(header)));
    }

    private static byte[] withLoopExtension(byte[] gif, String identifier, int loopCount) {
        byte[] id = identifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] extension = new byte[]{
                0x21, (byte) 0xFF, 11,
                id[0], id[1], id[2], id[3], id[4], id[5], id[6], id[7], id[8], id[9], id[10],
                3, 1, (byte) loopCount, (byte) (loopCount >>> 8), 0
        };
        int trailerOffset = gif.length - 1;
        byte[] result = new byte[gif.length + extension.length];
        System.arraycopy(gif, 0, result, 0, trailerOffset);
        System.arraycopy(extension, 0, result, trailerOffset, extension.length);
        result[result.length - 1] = 0x3B;
        return result;
    }

    private static byte[] singlePixelGif(boolean hasGlobalColorTable) {
        byte packed = hasGlobalColorTable ? (byte) 0x80 : 0;
        byte[] header = {
                'G', 'I', 'F', '8', '9', 'a',
                1, 0, 1, 0, packed, 0, 0
        };
        byte[] colorTable = hasGlobalColorTable
                ? new byte[]{(byte) 0xFF, 0, 0, 0, 0, (byte) 0xFF}
                : new byte[0];
        byte[] image = {
                0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0,
                2, 2, 0x44, 0x01, 0,
                0x3B
        };
        byte[] result = new byte[header.length + colorTable.length + image.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(colorTable, 0, result, header.length, colorTable.length);
        System.arraycopy(image, 0, result, header.length + colorTable.length, image.length);
        return result;
    }

    @Test
    void enforcesBoundedEncodedInput() {
        AnimatedImageLimits limits = new AnimatedImageLimits(8, 16, 256, 4, 1024, 64);
        assertThrows(IOException.class, () -> new GifDecoder(limits)
                .decode(new ByteArrayInputStream(new byte[9])));
    }
}
