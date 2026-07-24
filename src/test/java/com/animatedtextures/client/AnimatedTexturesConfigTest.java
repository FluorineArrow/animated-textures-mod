package com.animatedtextures.client;

import com.animatedtextures.util.AnimationQuality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimatedTexturesConfigTest {

    @Test
    void defaultsNullAndMalformedDocuments() {
        assertEquals(AnimatedTexturesConfig.ScalingMode.BILINEAR,
                AnimatedTexturesConfig.parse("null").scalingMode);
        assertEquals(AnimatedTexturesConfig.ScalingMode.BILINEAR,
                AnimatedTexturesConfig.parse("{\"scalingMode\":null}").scalingMode);
        assertEquals(AnimatedTexturesConfig.ScalingMode.BILINEAR,
                AnimatedTexturesConfig.parse("not json").scalingMode);
        assertEquals(AnimationQuality.STANDARD,
                AnimatedTexturesConfig.parse("{\"quality\":null}").quality);
    }

    @Test
    void acceptsLegacyRemovedKeysAndRetainsSupportedScalingMode() {
        AnimatedTexturesConfig config = AnimatedTexturesConfig.parse(
                "{\"scalingMode\":\"NEAREST\",\"enableMipmaps\":false,\"atlasSize\":4096,\"logLevel\":\"DEBUG\"}");

        assertEquals(AnimatedTexturesConfig.ScalingMode.NEAREST, config.scalingMode);
        assertEquals(AnimationQuality.STANDARD, config.quality);
    }

    @Test
    void parsesAndCopiesEveryQualityMode() {
        for (AnimationQuality quality : AnimationQuality.values()) {
            AnimatedTexturesConfig config = AnimatedTexturesConfig.parse(
                    "{\"quality\":\"" + quality.name() + "\"}");

            assertEquals(quality, config.quality);
            assertEquals(quality, config.copy().quality);
        }
    }
}
