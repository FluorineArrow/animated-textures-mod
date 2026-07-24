package com.animatedtextures.util;

import com.animatedtextures.client.AnimatedTexturesConfig;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparedFrameCacheTest {

    @Test
    void admitsOnlyCompleteAnimationCyclesThatFit() {
        AnimatedTexture twoFrameTexture = new AnimatedTexture(
                Identifier.of("minecraft", "textures/block/test.gif"),
                List.of(frame(0), frame(1)));
        PreparedFrameCache.Key key = new PreparedFrameCache.Key(twoFrameTexture, 0,
                4_096, 4_096, 0, AnimatedTexturesConfig.ScalingMode.NEAREST);

        assertFalse(new PreparedFrameCache(64L * 1024 * 1024).canCache(key));
        assertTrue(new PreparedFrameCache(192L * 1024 * 1024).canCache(key));
    }

    @Test
    void estimatesFullMipChainWithoutOverflow() {
        assertTrue(PreparedFrameCache.estimateMipChainBytes(4_096, 4_096, 12) > 64L * 1024 * 1024);
        assertTrue(PreparedFrameCache.estimateMipChainBytes(Integer.MAX_VALUE,
                Integer.MAX_VALUE, 30) > 0);
    }

    private static AnimatedFrame frame(int color) {
        return new AnimatedFrame(new int[]{0xFF000000 | color}, 1, 1, 50);
    }
}
