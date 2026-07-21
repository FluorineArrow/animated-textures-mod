package com.animatedtextures.util;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatedTextureRegistryTest {

    @Test
    void snapshotAndCollectionsAreImmutable() {
        AnimatedTextureRegistryBuilder builder = new AnimatedTextureRegistryBuilder();
        builder.tryAdd(texture("first"));
        AnimatedTextureRegistrySnapshot snapshot = builder.freeze();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.textures().put(Identifier.of("minecraft", "block/second"), texture("second")));
        assertThrows(IllegalStateException.class, () -> builder.tryAdd(texture("second")));
        assertEquals(1, snapshot.all().size());
    }

    @Test
    void duplicateTargetIsRejectedWithoutReplacingFirst() {
        AnimatedTextureRegistryBuilder builder = new AnimatedTextureRegistryBuilder();
        AnimatedTexture first = texture("same");

        assertTrue(builder.tryAdd(first));
        assertFalse(builder.tryAdd(texture("same")));
        assertEquals(first, builder.freeze().get(Identifier.of("minecraft", "block/same")));
    }

    private static AnimatedTexture texture(String name) {
        return new AnimatedTexture(Identifier.of("minecraft", "textures/block/" + name + ".gif"),
                new DecodedAnimation(List.of(new AnimatedFrame(new int[1], 1, 1, 50)), 1));
    }
}
