package com.animatedtextures.util;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatedTextureTest {

    @Test
    void advancesRevisionOnlyWhenTheFrameChanges() {
        AnimatedTexture texture = textureWithDurations(50, 50);

        assertFalse(texture.tick(49));
        assertTrue(texture.tick(1));
        assertEquals(1, texture.getCurrentFrameIndex());
        assertEquals(1, texture.getFrameRevision());
    }

    @Test
    void catchesUpAcrossFramesAndPreservesRemainder() {
        AnimatedTexture texture = textureWithDurations(50, 70, 90);

        assertTrue(texture.tick(125));
        assertEquals(2, texture.getCurrentFrameIndex());
        assertEquals(2, texture.getFrameRevision());
        assertFalse(texture.tick(84));
        assertTrue(texture.tick(1));
        assertEquals(0, texture.getCurrentFrameIndex());
        assertEquals(3, texture.getFrameRevision());
    }

    @Test
    void validatesElapsedTime() {
        AnimatedTexture texture = textureWithDurations(50, 50);

        assertFalse(texture.tick(0));
        assertThrows(IllegalArgumentException.class, () -> texture.tick(-1));
    }

    @Test
    void handlesLargeElapsedIntervals() {
        AnimatedTexture texture = textureWithDurations(50, 100, 150);

        assertTrue(texture.tick(30_050));
        assertEquals(1, texture.getCurrentFrameIndex());
        assertEquals(301, texture.getFrameRevision());
    }

    @Test
    void bilinearScalingUsesPixelCenters() {
        int[] resized = AnimatedTexture.resizeBilinearArgb(
                new int[]{0xFF000000, 0xFFFFFFFF}, 2, 1, 3, 1);

        assertEquals(0xFF000000, resized[0]);
        assertEquals(0xFF808080, resized[1]);
        assertEquals(0xFFFFFFFF, resized[2]);
    }

    @Test
    void bilinearScalingDoesNotLeakHiddenTransparentColor() {
        int[] resized = AnimatedTexture.resizeBilinearArgb(
                new int[]{0xFFFF0000, 0x000000FF}, 2, 1, 3, 1);

        assertEquals(0x80FF0000, resized[1]);
    }

    @Test
    void bilinearScalingNormalizesFullyTransparentPixels() {
        int[] resized = AnimatedTexture.resizeBilinearArgb(
                new int[]{0x00FF0000, 0x000000FF}, 2, 1, 3, 1);

        assertEquals(0, resized[1]);
    }

    @Test
    void bilinearScalingPreservesOnePixelAndChannelOrder() {
        int[] resized = AnimatedTexture.resizeBilinearArgb(new int[]{0xFF1234AB}, 1, 1, 3, 2);

        for (int pixel : resized) {
            assertEquals(0xFF1234AB, pixel);
        }
    }

    private static AnimatedTexture textureWithDurations(int... durations) {
        return new AnimatedTexture(Identifier.of("minecraft", "textures/block/example.gif"),
                textureFrames(durations));
    }

    private static List<AnimatedFrame> textureFrames(int... durations) {
        java.util.ArrayList<AnimatedFrame> frames = new java.util.ArrayList<>();
        for (int index = 0; index < durations.length; index++) {
            frames.add(new AnimatedFrame(new int[]{0xFF000000 | index}, 1, 1, durations[index]));
        }
        return frames;
    }

    @Test
    void overflowingElapsedAdditionPreservesModuloPosition() {
        AnimatedTexture texture = textureWithDurations(50, 50);

        assertFalse(texture.tick(49));
        assertTrue(texture.tick(Long.MAX_VALUE));
        assertEquals(1, texture.getCurrentFrameIndex());
        assertFalse(texture.tick(43));
        assertTrue(texture.tick(1));
        assertEquals(0, texture.getCurrentFrameIndex());
    }

    @Test
    void singleFrameFiniteBoundarySurvivesOverflowingElapsedSum() {
        long plays = Long.MAX_VALUE / 50 + 1;
        AnimatedTexture texture = new AnimatedTexture(
                Identifier.of("minecraft", "textures/block/example.gif"),
                new DecodedAnimation(textureFrames(50), plays));

        texture.tick(49);
        texture.tick(Long.MAX_VALUE);

        assertTrue(texture.isFinished());
    }

    @Test
    void finiteAnimationStopsOnItsFinalFrame() {
        AnimatedTexture texture = new AnimatedTexture(
                Identifier.of("minecraft", "textures/block/example.gif"),
                new DecodedAnimation(textureFrames(50, 50), 1));

        assertTrue(texture.tick(50));
        assertEquals(1, texture.getCurrentFrameIndex());
        assertFalse(texture.isFinished());
        assertFalse(texture.tick(50));
        assertTrue(texture.isFinished());
        assertEquals(1, texture.getCurrentFrameIndex());
        assertEquals(1, texture.getFrameRevision());
        assertFalse(texture.tick(1_000));
    }

    @Test
    void finiteAnimationCompletesTheDeclaredNumberOfPlays() {
        AnimatedTexture texture = new AnimatedTexture(
                Identifier.of("minecraft", "textures/block/example.gif"),
                new DecodedAnimation(textureFrames(50, 50), 2));

        assertTrue(texture.tick(150));
        assertEquals(1, texture.getCurrentFrameIndex());
        assertEquals(3, texture.getFrameRevision());
        assertFalse(texture.isFinished());
        assertFalse(texture.tick(50));
        assertTrue(texture.isFinished());
    }

    @Test
    void hugeFiniteAnimationCatchUpIsBounded() {
        AnimatedTexture texture = new AnimatedTexture(
                Identifier.of("minecraft", "textures/block/example.png3"),
                new DecodedAnimation(textureFrames(50, 50), 4_294_967_295L));

        assertTrue(texture.tick(Long.MAX_VALUE));
        assertTrue(texture.isFinished());
        assertEquals(1, texture.getCurrentFrameIndex());
        assertEquals(8_589_934_589L, texture.getFrameRevision());
    }

    @Test
    void singleFrameFiniteAnimationCanFinish() {
        AnimatedTexture texture = new AnimatedTexture(
                Identifier.of("minecraft", "textures/block/example.gif"),
                new DecodedAnimation(textureFrames(50), 2));

        assertFalse(texture.tick(99));
        assertFalse(texture.isFinished());
        assertFalse(texture.tick(1));
        assertTrue(texture.isFinished());
        assertEquals(0, texture.getFrameRevision());
    }

    @Test
    void rejectsMixedFrameDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new AnimatedTexture(
                Identifier.of("minecraft", "textures/block/example.gif"),
                List.of(new AnimatedFrame(new int[1], 1, 1, 50), new AnimatedFrame(new int[4], 2, 2, 50))));
    }

    @Test
    void scopesBareAliasesToTheirAtlas() {
        AnimatedTexture gui = new AnimatedTexture(Identifier.of("minecraft", "textures/gui/sprites/test.gif"),
                List.of(new AnimatedFrame(new int[1], 1, 1, 50)));

        assertEquals(List.of(Identifier.of("minecraft", "gui/sprites/test")),
                gui.getSpriteIdCandidates(Identifier.ofVanilla("textures/atlas/blocks.png")));
        assertEquals(List.of(Identifier.of("minecraft", "gui/sprites/test"), Identifier.of("minecraft", "test")),
                gui.getSpriteIdCandidates(Identifier.ofVanilla("textures/atlas/gui.png")));
    }
}
