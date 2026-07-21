package com.animatedtextures.util;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Strict APNG decoder for .png3 resource-pack assets.
 */
public class ApngDecoder {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final String IHDR = "IHDR";
    private static final String IDAT = "IDAT";
    private static final String IEND = "IEND";
    private static final String ACTL = "acTL";
    private static final String FCTL = "fcTL";
    private static final String FDAT = "fdAT";

    private final AnimatedImageLimits limits;

    ApngDecoder(AnimatedImageLimits limits) {
        this.limits = limits;
    }

    public ApngDecoder(AnimatedTextureReloadBudget.Remaining remaining) {
        this(AnimatedImageLimits.DEFAULT.forRemaining(remaining));
    }

    public ApngDecoder() {
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
        return parse(limits.readBounded(stream, "APNG"));
    }

    private DecodedAnimation parse(byte[] bytes) throws Exception {
        if (bytes.length < PNG_SIGNATURE.length || !Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(bytes, PNG_SIGNATURE.length))) {
            throw new IOException("Not a PNG file");
        }

        int offset = PNG_SIGNATURE.length;
        boolean seenIhdr = false;
        boolean seenActl = false;
        boolean seenIdat = false;
        boolean seenIend = false;
        int expectedSequence = 0;
        int declaredFrames = -1;
        long totalPlays = DecodedAnimation.INFINITE_PLAYS;
        int canvasWidth = 0;
        int canvasHeight = 0;
        int bitDepth = -1;
        int colorType = -1;
        boolean seenPlte = false;
        boolean seenTrns = false;
        int paletteEntries = 0;
        byte[] ihdr = null;
        List<PngChunk> ancillary = new ArrayList<>();
        int ancillaryBytes = 0;
        List<FrameData> frames = new ArrayList<>();
        FrameData currentFrame = null;

        while (offset < bytes.length) {
            if (bytes.length - offset < 12) {
                throw new IOException("Truncated PNG chunk envelope");
            }
            long declaredLength = readUnsignedInt(bytes, offset);
            offset += 4;
            String type = readType(bytes, offset);
            offset += 4;
            if (declaredLength > Integer.MAX_VALUE || declaredLength > bytes.length - offset - 4L) {
                throw new IOException("PNG chunk length is invalid for " + type);
            }
            int length = (int) declaredLength;
            byte[] data = Arrays.copyOfRange(bytes, offset, offset + length);
            offset += length;
            long expectedCrc = readUnsignedInt(bytes, offset);
            offset += 4;
            verifyCrc(type, data, expectedCrc);

            if (!seenIhdr && !IHDR.equals(type)) {
                throw new IOException("PNG IHDR must be the first chunk");
            }
            if (seenIend) {
                throw new IOException("PNG data appears after IEND");
            }

            switch (type) {
                case IHDR -> {
                    if (seenIhdr || length != 13) {
                        throw new IOException("PNG must contain one 13-byte IHDR");
                    }
                    canvasWidth = readPositiveInt(data, 0, "APNG canvas width");
                    canvasHeight = readPositiveInt(data, 4, "APNG canvas height");
                    limits.checkedPixels(canvasWidth, canvasHeight, "APNG canvas");
                    validateIhdr(data);
                    bitDepth = data[8] & 0xFF;
                    colorType = data[9] & 0xFF;
                    ihdr = data;
                    seenIhdr = true;
                }
                case ACTL -> {
                    if (!seenIhdr || seenActl || seenIdat || length != 8) {
                        throw new IOException("APNG acTL must appear once before IDAT and contain eight bytes");
                    }
                    declaredFrames = readPositiveInt(data, 0, "APNG frame count");
                    long declaredPlays = readUnsignedInt(data, 4);
                    totalPlays = declaredPlays == 0 ? DecodedAnimation.INFINITE_PLAYS : declaredPlays;
                    if (declaredFrames > limits.maxFrames) {
                        throw new IOException("APNG exceeds the frame limit of " + limits.maxFrames);
                    }
                    long declaredPixels = (long) declaredFrames * limits.checkedPixels(
                            canvasWidth, canvasHeight, "APNG declared output canvas");
                    if (declaredPixels > limits.maxTotalPixels) {
                        throw new IOException("APNG declared output exceeds the retained pixel limit of "
                                + limits.maxTotalPixels);
                    }
                    seenActl = true;
                }
                case FCTL -> {
                    if (!seenActl || length != 26) {
                        throw new IOException("APNG fcTL requires acTL and exactly 26 bytes");
                    }
                    int sequence = readInt(data, 0);
                    if (sequence != expectedSequence++) {
                        throw new IOException("APNG frame-control sequence is out of order");
                    }
                    if (frames.size() >= limits.maxFrames) {
                        throw new IOException("APNG exceeds the frame limit of " + limits.maxFrames);
                    }
                    FrameControl control = parseFrameControl(data, canvasWidth, canvasHeight);
                    boolean defaultImageFrame = !seenIdat && frames.isEmpty();
                    currentFrame = new FrameData(control, defaultImageFrame);
                    frames.add(currentFrame);
                }
                case IDAT -> {
                    if (!seenActl) {
                        throw new IOException(".png3 must contain APNG animation control data");
                    }
                    if (colorType == 3 && !seenPlte) {
                        throw new IOException("Indexed PNG image data requires a preceding PLTE");
                    }
                    if (currentFrame != null) {
                        if (!currentFrame.usesDefaultImageData) {
                            throw new IOException("APNG IDAT is not associated with the initial animation frame");
                        }
                        currentFrame.dataChunks.add(data);
                    } else if (!frames.isEmpty()) {
                        throw new IOException("APNG IDAT appears after animated frames have started");
                    }
                    seenIdat = true;
                }
                case FDAT -> {
                    if (length < 4 || currentFrame == null || currentFrame.usesDefaultImageData) {
                        throw new IOException("APNG fdAT has no active non-default frame");
                    }
                    int sequence = readInt(data, 0);
                    if (sequence != expectedSequence++) {
                        throw new IOException("APNG frame-data sequence is out of order");
                    }
                    currentFrame.dataChunks.add(Arrays.copyOfRange(data, 4, data.length));
                }
                case IEND -> {
                    if (length != 0) {
                        throw new IOException("PNG IEND must be empty");
                    }
                    seenIend = true;
                }
                case "PLTE" -> {
                    if (seenPlte || seenTrns || seenIdat) {
                        throw new IOException("PNG PLTE must appear once before tRNS and image data");
                    }
                    if (colorType == 0 || colorType == 4) {
                        throw new IOException("PNG PLTE is not permitted for grayscale color type " + colorType);
                    }
                    if (length == 0 || length % 3 != 0 || length > 256 * 3) {
                        throw new IOException("PNG PLTE must contain between one and 256 RGB entries");
                    }
                    paletteEntries = length / 3;
                    if (colorType == 3 && paletteEntries > (1 << bitDepth)) {
                        throw new IOException("PNG PLTE has more entries than indexed bit depth permits");
                    }
                    ancillaryBytes = reserveAncillaryBytes(ancillaryBytes, data.length);
                    ancillary.add(new PngChunk(type, data));
                    seenPlte = true;
                }
                case "tRNS" -> {
                    if (seenTrns || seenIdat) {
                        throw new IOException("PNG tRNS must appear once before image data");
                    }
                    switch (colorType) {
                        case 0 -> {
                            if (length != 2) {
                                throw new IOException("Grayscale PNG tRNS must contain two bytes");
                            }
                        }
                        case 2 -> {
                            if (length != 6) {
                                throw new IOException("Truecolor PNG tRNS must contain six bytes");
                            }
                        }
                        case 3 -> {
                            if (!seenPlte || length == 0 || length > paletteEntries) {
                                throw new IOException("Indexed PNG tRNS requires a preceding PLTE and between one and one alpha entry per palette entry");
                            }
                        }
                        default -> throw new IOException("PNG tRNS is not permitted for color type " + colorType);
                    }
                    ancillaryBytes = reserveAncillaryBytes(ancillaryBytes, data.length);
                    ancillary.add(new PngChunk(type, data));
                    seenTrns = true;
                }
                default -> {
                    if (isCritical(type)) {
                        throw new IOException("Unsupported critical PNG chunk " + type);
                    }
                }
            }
        }

        if (!seenIhdr || !seenActl || !seenIend || frames.isEmpty() || declaredFrames != frames.size()) {
            throw new IOException("APNG chunk structure does not describe a complete animation");
        }
        for (FrameData frame : frames) {
            if (frame.dataChunks.isEmpty()) {
                throw new IOException("APNG animation frame has no image data");
            }
        }

        return new DecodedAnimation(compositeFrames(ihdr, canvasWidth, canvasHeight, ancillary, frames), totalPlays);
    }

    private List<AnimatedFrame> compositeFrames(byte[] ihdr, int canvasWidth, int canvasHeight,
                                                 List<PngChunk> ancillary, List<FrameData> frameData) throws Exception {
        int bitDepth = ihdr[8] & 0xFF;
        int colorType = ihdr[9] & 0xFF;
        int compression = ihdr[10] & 0xFF;
        int filter = ihdr[11] & 0xFF;
        int interlace = ihdr[12] & 0xFF;
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        List<AnimatedFrame> result = new ArrayList<>(frameData.size());
        long retainedPixels = 0;

        for (FrameData frame : frameData) {
            FrameControl control = frame.control;
            limits.reserveFrame(result.size(), retainedPixels,
                    limits.checkedPixels(canvasWidth, canvasHeight, "APNG output frame"), "APNG");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(buildPng(
                    control.width, control.height, bitDepth, colorType, compression, filter, interlace,
                    ancillary, frame.dataChunks)));
            if (image == null || image.getWidth() != control.width || image.getHeight() != control.height) {
                throw new IOException("APNG frame cannot be decoded at its announced dimensions");
            }

            BufferedImage beforeDraw = control.disposeOp == 2 ? copyImage(canvas) : null;
            Graphics2D graphics = canvas.createGraphics();
            graphics.setComposite(control.blendOp == 0 ? AlphaComposite.Src : AlphaComposite.SrcOver);
            graphics.drawImage(image, control.xOffset, control.yOffset, null);
            graphics.dispose();

            result.add(new AnimatedFrame(canvas, durationMs(control)));
            retainedPixels += (long) canvasWidth * canvasHeight;

            if (control.disposeOp == 1) {
                Graphics2D clear = canvas.createGraphics();
                clear.setComposite(AlphaComposite.Clear);
                clear.fillRect(control.xOffset, control.yOffset, control.width, control.height);
                clear.dispose();
            } else if (control.disposeOp == 2) {
                canvas = beforeDraw;
            }
        }
        return List.copyOf(result);
    }

    private FrameControl parseFrameControl(byte[] data, int canvasWidth, int canvasHeight) throws IOException {
        int width = readPositiveInt(data, 4, "APNG frame width");
        int height = readPositiveInt(data, 8, "APNG frame height");
        limits.checkedPixels(width, height, "APNG frame");
        int xOffset = readNonNegativeInt(data, 12, "APNG frame X offset");
        int yOffset = readNonNegativeInt(data, 16, "APNG frame Y offset");
        if ((long) xOffset + width > canvasWidth || (long) yOffset + height > canvasHeight) {
            throw new IOException("APNG frame lies outside the canvas");
        }
        int disposeOp = data[24] & 0xFF;
        int blendOp = data[25] & 0xFF;
        if (disposeOp > 2 || blendOp > 1) {
            throw new IOException("APNG frame uses an unsupported blend or disposal operation");
        }
        return new FrameControl(width, height, xOffset, yOffset,
                readShort(data, 20), readShort(data, 22), disposeOp, blendOp);
    }

    private void validateIhdr(byte[] ihdr) throws IOException {
        int bitDepth = ihdr[8] & 0xFF;
        int colorType = ihdr[9] & 0xFF;
        int compression = ihdr[10] & 0xFF;
        int filter = ihdr[11] & 0xFF;
        int interlace = ihdr[12] & 0xFF;
        if (compression != 0 || filter != 0 || interlace > 1 || !isValidColorType(bitDepth, colorType)) {
            throw new IOException("PNG IHDR uses unsupported image parameters");
        }
    }

    private boolean isValidColorType(int bitDepth, int colorType) {
        return switch (colorType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            default -> false;
        };
    }

    private byte[] buildPng(int width, int height, int bitDepth, int colorType, int compression,
                            int filter, int interlace, List<PngChunk> ancillary, List<byte[]> imageData) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(PNG_SIGNATURE);
        byte[] header = new byte[13];
        writeInt(header, 0, width);
        writeInt(header, 4, height);
        header[8] = (byte) bitDepth;
        header[9] = (byte) colorType;
        header[10] = (byte) compression;
        header[11] = (byte) filter;
        header[12] = (byte) interlace;
        writeChunk(output, IHDR, header);
        for (PngChunk chunk : ancillary) {
            writeChunk(output, chunk.type, chunk.data);
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        for (byte[] chunk : imageData) {
            compressed.write(chunk);
        }
        writeChunk(output, IDAT, compressed.toByteArray());
        writeChunk(output, IEND, new byte[0]);
        return output.toByteArray();
    }

    private void writeChunk(OutputStream output, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] length = new byte[4];
        writeInt(length, 0, data.length);
        output.write(length);
        output.write(typeBytes);
        output.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        writeInt(crcBytes, 0, (int) crc.getValue());
        output.write(crcBytes);
    }

    private void verifyCrc(String type, byte[] data, long expected) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(type.getBytes(StandardCharsets.US_ASCII));
        crc.update(data);
        if (crc.getValue() != expected) {
            throw new IOException("PNG CRC mismatch for " + type);
        }
    }

    private int reserveAncillaryBytes(int currentBytes, int dataLength) throws IOException {
        if (currentBytes > limits.maxAncillaryBytes - dataLength) {
            throw new IOException("APNG palette/transparency data exceeds the ancillary-data limit");
        }
        return currentBytes + dataLength;
    }

    private boolean isCritical(String type) {
        return type.charAt(0) >= 'A' && type.charAt(0) <= 'Z';
    }

    private int durationMs(FrameControl control) {
        int denominator = control.delayDenominator == 0 ? 100 : control.delayDenominator;
        return (int) ((long) control.delayNumerator * 1000 / denominator);
    }

    private BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static long readUnsignedInt(byte[] data, int offset) {
        return Integer.toUnsignedLong(readInt(data, offset));
    }

    private static int readPositiveInt(byte[] data, int offset, String description) throws IOException {
        int value = readInt(data, offset);
        if (value <= 0) {
            throw new IOException(description + " must be positive");
        }
        return value;
    }

    private static int readNonNegativeInt(byte[] data, int offset, String description) throws IOException {
        int value = readInt(data, offset);
        if (value < 0) {
            throw new IOException(description + " must not exceed the signed integer range");
        }
        return value;
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24
                | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8
                | data[offset + 3] & 0xFF;
    }

    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }

    private static String readType(byte[] data, int offset) throws IOException {
        String type = new String(data, offset, 4, StandardCharsets.US_ASCII);
        for (int index = 0; index < type.length(); index++) {
            char value = type.charAt(index);
            if ((value < 'A' || value > 'Z') && (value < 'a' || value > 'z')) {
                throw new IOException("PNG chunk type contains invalid characters");
            }
        }
        return type;
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private record PngChunk(String type, byte[] data) {
    }

    private record FrameControl(int width, int height, int xOffset, int yOffset,
                                int delayNumerator, int delayDenominator, int disposeOp, int blendOp) {
    }

    private static final class FrameData {
        private final FrameControl control;
        private final boolean usesDefaultImageData;
        private final List<byte[]> dataChunks = new ArrayList<>();

        private FrameData(FrameControl control, boolean usesDefaultImageData) {
            this.control = control;
            this.usesDefaultImageData = usesDefaultImageData;
        }
    }
}
