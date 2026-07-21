package com.animatedtextures.client;

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
    }

    @Test
    void acceptsLegacyRemovedKeysAndRetainsSupportedScalingMode() {
        AnimatedTexturesConfig config = AnimatedTexturesConfig.parse(
                "{\"scalingMode\":\"NEAREST\",\"enableMipmaps\":false,\"atlasSize\":4096,\"logLevel\":\"DEBUG\"}");

        assertEquals(AnimatedTexturesConfig.ScalingMode.NEAREST, config.scalingMode);
    }
}
