package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApngDecoderTest {

    @Test
    void decodesAValidTwoFrameApng() throws Exception {
        var frames = new ApngDecoder().decode(new ByteArrayInputStream(twoFrameApng()));

        assertEquals(2, frames.size());
        assertArrayEquals(new int[]{0xFFFF0000}, frames.get(0).getPixels());
        assertArrayEquals(new int[]{0xFF0000FF}, frames.get(1).getPixels());
        assertEquals(100, frames.get(0).getDurationMs());
    }

    @Test
    void preservesApngPlayCount() throws Exception {
        assertEquals(DecodedAnimation.INFINITE_PLAYS,
                new ApngDecoder().decodeAnimation(new ByteArrayInputStream(twoFrameApng(0))).totalPlays());
        assertEquals(1,
                new ApngDecoder().decodeAnimation(new ByteArrayInputStream(twoFrameApng(1))).totalPlays());
        assertEquals(2,
                new ApngDecoder().decodeAnimation(new ByteArrayInputStream(twoFrameApng(2))).totalPlays());
        assertEquals(4_294_967_295L,
                new ApngDecoder().decodeAnimation(new ByteArrayInputStream(twoFrameApng(-1))).totalPlays());
    }

    @Test
    void rejectsCrcCorruption() {
        byte[] corrupted = twoFrameApng();
        corrupted[corrupted.length - 1] ^= 1;

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(corrupted)));
    }

    @Test
    void rejectsFrameOutsideCanvas() {
        byte[] fixture = twoFrameApng();
        int typeOffset = indexOf(fixture, "fcTL".getBytes(StandardCharsets.US_ASCII));
        int frameControlOffset = typeOffset + 4;
        fixture[frameControlOffset + 12] = 0;
        fixture[frameControlOffset + 13] = 0;
        fixture[frameControlOffset + 14] = 0;
        fixture[frameControlOffset + 15] = 1;
        rewriteChunkCrc(fixture, typeOffset - 4);

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void rejectsPaletteAfterImageData() {
        byte[] fixture = insertBeforeChunk(twoFrameApng(), "IEND", chunk("PLTE", new byte[]{(byte) 0xFF, 0, 0}));

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void rejectsDuplicatePalette() {
        byte[] fixture = indexedApng(
                chunk("PLTE", new byte[]{(byte) 0xFF, 0, 0}),
                chunk("PLTE", new byte[]{0, 0, (byte) 0xFF}));

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void rejectsTransparencyForRgba() {
        byte[] fixture = insertBeforeChunk(twoFrameApng(), "IDAT", chunk("tRNS", new byte[]{0}));

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void rejectsIndexedTransparencyBeforePalette() {
        byte[] fixture = indexedApng(
                chunk("tRNS", new byte[]{0}),
                chunk("PLTE", new byte[]{(byte) 0xFF, 0, 0}));

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void rejectsIndexedImageWithoutPalette() {
        byte[] fixture = indexedApng();

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void rejectsMalformedPaletteLength() {
        byte[] fixture = indexedApng(chunk("PLTE", new byte[]{(byte) 0xFF, 0}));

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void rejectsIndexedTransparencyLargerThanPalette() {
        byte[] fixture = indexedApng(
                chunk("PLTE", new byte[]{(byte) 0xFF, 0, 0}),
                chunk("tRNS", new byte[]{(byte) 0xFF, 0}));

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void rejectsDuplicateTransparency() {
        byte[] fixture = indexedApng(
                chunk("PLTE", new byte[]{(byte) 0xFF, 0, 0}),
                chunk("tRNS", new byte[]{(byte) 0xFF}),
                chunk("tRNS", new byte[]{(byte) 0xFF}));

        assertThrows(IOException.class, () -> new ApngDecoder().decode(new ByteArrayInputStream(fixture)));
    }

    @Test
    void decodesValidIndexedPaletteAndTransparency() throws Exception {
        byte[] fixture = indexedApng(
                chunk("PLTE", new byte[]{(byte) 0xFF, 0, 0}),
                chunk("tRNS", new byte[]{(byte) 0xFF}));

        var frames = new ApngDecoder().decode(new ByteArrayInputStream(fixture));

        assertEquals(1, frames.size());
        assertArrayEquals(new int[]{0xFFFF0000}, frames.get(0).getPixels());
    }

    @Test
    void enforcesBoundedEncodedInput() {
        AnimatedImageLimits limits = new AnimatedImageLimits(8, 16, 256, 4, 1024, 64);
        assertThrows(IOException.class, () -> new ApngDecoder(limits)
                .decode(new ByteArrayInputStream(new byte[9])));
    }

    private static byte[] twoFrameApng() {
        return twoFrameApng(0);
    }

    private static byte[] twoFrameApng(int totalPlays) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        output.writeBytes(chunk("IHDR", concat(intBytes(1), intBytes(1), new byte[]{8, 6, 0, 0, 0})));
        output.writeBytes(chunk("acTL", concat(intBytes(2), intBytes(totalPlays))));
        output.writeBytes(chunk("fcTL", frameControl(0)));
        output.writeBytes(chunk("IDAT", compressedRgba(255, 0, 0, 255)));
        output.writeBytes(chunk("fcTL", frameControl(1)));
        output.writeBytes(chunk("fdAT", concat(intBytes(2), compressedRgba(0, 0, 255, 255))));
        output.writeBytes(chunk("IEND", new byte[0]));
        return output.toByteArray();
    }

    private static byte[] indexedApng(byte[]... paletteChunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        output.writeBytes(chunk("IHDR", concat(intBytes(1), intBytes(1), new byte[]{8, 3, 0, 0, 0})));
        output.writeBytes(chunk("acTL", concat(intBytes(1), intBytes(0))));
        output.writeBytes(chunk("fcTL", frameControl(0)));
        for (byte[] paletteChunk : paletteChunks) {
            output.writeBytes(paletteChunk);
        }
        output.writeBytes(chunk("IDAT", compressedBytes(new byte[]{0, 0})));
        output.writeBytes(chunk("IEND", new byte[0]));
        return output.toByteArray();
    }

    private static byte[] insertBeforeChunk(byte[] fixture, String type, byte[] insertedChunk) {
        int typeOffset = indexOf(fixture, type.getBytes(StandardCharsets.US_ASCII));
        int chunkOffset = typeOffset - 4;
        return concat(java.util.Arrays.copyOfRange(fixture, 0, chunkOffset), insertedChunk,
                java.util.Arrays.copyOfRange(fixture, chunkOffset, fixture.length));
    }

    private static byte[] frameControl(int sequence) {
        return concat(intBytes(sequence), intBytes(1), intBytes(1), intBytes(0), intBytes(0),
                new byte[]{0, 1, 0, 10, 0, 0});
    }

    private static byte[] compressedRgba(int red, int green, int blue, int alpha) {
        return compressedBytes(new byte[]{0, (byte) red, (byte) green, (byte) blue, (byte) alpha});
    }

    private static byte[] compressedBytes(byte[] input) {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64];
        while (!deflater.finished()) {
            output.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return output.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return concat(intBytes(data.length), typeBytes, data, intBytes((int) crc.getValue()));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.writeBytes(part);
        }
        return output.toByteArray();
    }

    private static byte[] intBytes(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    private static int indexOf(byte[] bytes, byte[] target) {
        for (int index = 0; index <= bytes.length - target.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < target.length; offset++) {
                if (bytes[index + offset] != target[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return index;
            }
        }
        throw new AssertionError("Target not found");
    }

    private static void rewriteChunkCrc(byte[] bytes, int lengthOffset) {
        int length = ((bytes[lengthOffset] & 0xFF) << 24) | ((bytes[lengthOffset + 1] & 0xFF) << 16)
                | ((bytes[lengthOffset + 2] & 0xFF) << 8) | (bytes[lengthOffset + 3] & 0xFF);
        int typeOffset = lengthOffset + 4;
        CRC32 crc = new CRC32();
        crc.update(bytes, typeOffset, 4 + length);
        int crcOffset = typeOffset + 4 + length;
        byte[] value = intBytes((int) crc.getValue());
        System.arraycopy(value, 0, bytes, crcOffset, value.length);
    }
}
