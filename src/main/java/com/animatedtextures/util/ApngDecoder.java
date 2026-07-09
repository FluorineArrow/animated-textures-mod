package com.animatedtextures.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * APNG (Animated PNG) decoder.
 *
 * APNG is a PNG extension that stores animation frames in fcTL/fdAT chunks.
 * Regular PNG readers ignore these chunks, so we parse them manually here.
 *
 * Spec: https://wiki.mozilla.org/APNG_Spec
 */
public class ApngDecoder {

    /** Maximum APNG file size (100 MB) to prevent OOM from malicious resource packs. */
    private static final int MAX_FILE_SIZE = 100 * 1024 * 1024;

    // PNG chunk type constants
    private static final int CHUNK_IHDR = chunkType("IHDR");
    private static final int CHUNK_IDAT = chunkType("IDAT");
    private static final int CHUNK_IEND = chunkType("IEND");
    private static final int CHUNK_acTL = chunkType("acTL");
    private static final int CHUNK_fcTL = chunkType("fcTL");
    private static final int CHUNK_fdAT = chunkType("fdAT");

    private static int chunkType(String s) {
        byte[] b = s.getBytes();
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    // PNG magic bytes
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    // ---- Per-frame metadata (fcTL) ----
    private static class FrameControl {
        int sequenceNumber;
        int width, height;
        int xOffset, yOffset;
        int delayNum, delayDen; // delay = delayNum / delayDen seconds
        int disposeOp;   // 0=NONE, 1=BACKGROUND, 2=PREVIOUS
        int blendOp;     // 0=SOURCE, 1=OVER
    }

    public List<AnimatedFrame> decode(InputStream stream) throws Exception {
        byte[] data = stream.readAllBytes();
        if (data.length > MAX_FILE_SIZE) {
            throw new Exception("APNG file too large: " + data.length + " bytes (max " + MAX_FILE_SIZE + ")");
        }
        return parseApng(data);
    }

    private List<AnimatedFrame> parseApng(byte[] data) throws Exception {
        // Verify PNG signature
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) throw new Exception("Not a PNG file");
        }

        int pos = 8; // after signature
        byte[] ihdrData = null;
        byte[] defaultImageData = null; // for first-frame-as-default-image
        List<FrameControl> frameControls = new ArrayList<>();
        List<List<byte[]>> frameDatChunks = new ArrayList<>(); // raw IDAT/fdAT per frame
        List<byte[]> currentFrameDatChunks = null;

        int frameIndex = -1;
        boolean hasActl = false;
        int totalFrames = 0;
        List<byte[]> idatChunks = new ArrayList<>(); // default image IDAT

        // Keep original palette/transparency/gamma chunks for re-assembling PNGs
        List<byte[][]> ancillaryChunks = new ArrayList<>(); // [type, data]

        while (pos < data.length - 4) {
            int length = readInt(data, pos); pos += 4;
            int type = readInt(data, pos); pos += 4;
            byte[] chunkData = new byte[length];
            System.arraycopy(data, pos, chunkData, 0, length);
            pos += length;
            int crc = readInt(data, pos); pos += 4;

            if (type == CHUNK_IHDR) {
                ihdrData = chunkData;

            } else if (type == CHUNK_acTL) {
                hasActl = true;
                totalFrames = readInt(chunkData, 0);

            } else if (type == CHUNK_fcTL) {
                // fcTL chunk must be exactly 26 bytes per APNG spec
                if (length < 26) {
                    System.err.println("[AnimatedTextures] APNG: fcTL chunk too short (" + length + " bytes, need 26), skipping frame.");
                    // pos already advanced past chunk data; just skip this frame
                } else {
                    FrameControl fc = new FrameControl();
                    fc.sequenceNumber = readInt(chunkData, 0);
                    fc.width = readInt(chunkData, 4);
                    fc.height = readInt(chunkData, 8);
                    fc.xOffset = readInt(chunkData, 12);
                    fc.yOffset = readInt(chunkData, 16);
                    fc.delayNum = readShort(chunkData, 20);
                    fc.delayDen = readShort(chunkData, 22);
                    fc.disposeOp = chunkData[24] & 0xFF;
                    fc.blendOp = chunkData[25] & 0xFF;
                    frameControls.add(fc);
                    currentFrameDatChunks = new ArrayList<>();
                    frameDatChunks.add(currentFrameDatChunks);
                    frameIndex++;
                }

            } else if (type == CHUNK_IDAT) {
                idatChunks.add(chunkData);
                // If no fcTL seen yet, these are for the default image
                if (currentFrameDatChunks != null) {
                    currentFrameDatChunks.add(chunkData);
                }

            } else if (type == CHUNK_fdAT) {
                // fdAT: 4 bytes sequence number, rest is compressed image data
                byte[] imgData = new byte[length - 4];
                System.arraycopy(chunkData, 4, imgData, 0, imgData.length);
                if (currentFrameDatChunks != null) {
                    currentFrameDatChunks.add(imgData);
                }

            } else if (type == CHUNK_IEND) {
                break;

            } else {
                // Ancillary chunk (PLTE, tRNS, gAMA, etc.) - preserve for sub-image reconstruction
                byte[] typeBytes = new byte[4];
                typeBytes[0] = (byte) ((type >> 24) & 0xFF);
                typeBytes[1] = (byte) ((type >> 16) & 0xFF);
                typeBytes[2] = (byte) ((type >> 8) & 0xFF);
                typeBytes[3] = (byte) (type & 0xFF);
                ancillaryChunks.add(new byte[][]{typeBytes, chunkData});
            }
        }

        if (!hasActl || frameControls.isEmpty()) {
            // Not animated - treat as single static frame
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(
                    java.util.Arrays.copyOf(data, data.length)));
            if (img == null) throw new Exception("Failed to decode PNG");
            return List.of(new AnimatedFrame(img, 100));
        }

        // Reconstruct each frame as a standalone PNG and decode it
        int canvasWidth = readInt(ihdrData, 0);
        int canvasHeight = readInt(ihdrData, 4);
        int bitDepth = ihdrData[8] & 0xFF;
        int colorType = ihdrData[9] & 0xFF;
        int compressionMethod = ihdrData[10] & 0xFF;
        int filterMethod = ihdrData[11] & 0xFF;
        int interlaceMethod = ihdrData[12] & 0xFF;

        List<AnimatedFrame> result = new ArrayList<>();

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);

        for (int fi = 0; fi < frameControls.size(); fi++) {
            FrameControl fc = frameControls.get(fi);

            // Safety check: frameDatChunks may have fewer entries if the APNG is malformed
            if (fi >= frameDatChunks.size()) {
                System.err.println("[AnimatedTextures] APNG: fcTL/fdAT count mismatch at frame " + fi + ", truncating.");
                break;
            }
            List<byte[]> datChunks = frameDatChunks.get(fi);
            if (datChunks == null || datChunks.isEmpty()) {
                System.err.println("[AnimatedTextures] APNG: frame " + fi + " has no image data, skipping.");
                continue;
            }

            // Reconstruct a valid PNG for this frame's sub-image
            byte[] framePng = buildPng(fc.width, fc.height, bitDepth, colorType,
                    compressionMethod, filterMethod, interlaceMethod,
                    ancillaryChunks, datChunks);

            BufferedImage frameImg = ImageIO.read(new ByteArrayInputStream(framePng));
            if (frameImg == null) continue;

            // Save canvas state BEFORE drawing, for DISPOSE_OP_PREVIOUS.
            // Must be saved every frame (not just when disposeOp==2) because
            // consecutive DISPOSE_OP_PREVIOUS frames each need their own pre-draw snapshot.
            BufferedImage canvasBeforeDraw = (fc.disposeOp == 2) ? copyImage(canvas) : null;

            // Blend frame onto canvas
            Graphics2D g = canvas.createGraphics();
            if (fc.blendOp == 0) {
                // SOURCE: overwrite with frame pixels (including alpha)
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
            } else {
                // OVER: normal alpha compositing
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
            }
            g.drawImage(frameImg, fc.xOffset, fc.yOffset, null);
            g.dispose();

            // Capture canvas state as this frame
            int durationMs = computeDurationMs(fc);
            result.add(new AnimatedFrame(copyImage(canvas), durationMs));

            // Apply dispose operation
            if (fc.disposeOp == 1) {
                // DISPOSE_OP_BACKGROUND: clear frame region to transparent
                Graphics2D gc = canvas.createGraphics();
                gc.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
                gc.fillRect(fc.xOffset, fc.yOffset, fc.width, fc.height);
                gc.dispose();
            } else if (fc.disposeOp == 2 && canvasBeforeDraw != null) {
                // DISPOSE_OP_PREVIOUS: restore canvas to state before this frame was drawn
                canvas = canvasBeforeDraw;
            }
            // DISPOSE_OP_NONE (0): keep canvas as-is
        }

        return result.isEmpty() ? List.of(new AnimatedFrame(canvas, 100)) : result;
    }

    private int computeDurationMs(FrameControl fc) {
        if (fc.delayDen == 0) return fc.delayNum * 10; // assume /100 if den is 0
        return (int) ((fc.delayNum * 1000L) / fc.delayDen);
    }

    private BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    /**
     * Assemble a valid PNG byte stream from raw chunks.
     */
    private byte[] buildPng(int w, int h, int bitDepth, int colorType,
                             int compressionMethod, int filterMethod, int interlaceMethod,
                             List<byte[][]> ancillary, List<byte[]> idatChunks) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Signature
        out.write(PNG_SIGNATURE);

        // IHDR
        byte[] ihdr = new byte[13];
        writeInt(ihdr, 0, w);
        writeInt(ihdr, 4, h);
        ihdr[8] = (byte) bitDepth;
        ihdr[9] = (byte) colorType;
        ihdr[10] = (byte) compressionMethod;
        ihdr[11] = (byte) filterMethod;
        ihdr[12] = (byte) interlaceMethod;
        writeChunk(out, "IHDR", ihdr);

        // Ancillary chunks (PLTE, tRNS, etc.)
        for (byte[][] chunk : ancillary) {
            writeChunk(out, new String(chunk[0]), chunk[1]);
        }

        // IDAT chunks (combine all into one)
        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        for (byte[] dat : idatChunks) combined.write(dat);
        writeChunk(out, "IDAT", combined.toByteArray());

        // IEND
        writeChunk(out, "IEND", new byte[0]);

        return out.toByteArray();
    }

    private void writeChunk(OutputStream out, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes();
        // Length
        byte[] len = new byte[4];
        writeInt(len, 0, data.length);
        out.write(len);
        // Type
        out.write(typeBytes);
        // Data
        out.write(data);
        // CRC (over type + data)
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        writeInt(crcBytes, 0, (int) crc.getValue());
        out.write(crcBytes);
    }

    // ---- Byte utilities ----

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static int readShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }
}
