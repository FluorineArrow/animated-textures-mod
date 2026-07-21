package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasReloadGenerationTest {

    @Test
    void activatesOnlyValuesFromTheCurrentIdentityToken() {
        AtlasReloadGeneration<String, String> generations = new AtlasReloadGeneration<>();
        AtlasReloadGeneration.Token first = generations.currentToken();
        generations.record(first, "atlas", "first");

        assertEquals(Map.of("atlas", "first"), generations.activate(first));

        AtlasReloadGeneration.Token second = generations.currentToken();
        generations.record(first, "stale", "ignored");
        generations.record(second, "atlas", "second");
        assertEquals(Map.of("atlas", "second"), generations.activate(second));
    }

    @Test
    void latestAtlasUploadWinsWithinOneGeneration() {
        AtlasReloadGeneration<String, String> generations = new AtlasReloadGeneration<>();
        AtlasReloadGeneration.Token token = generations.currentToken();
        generations.record(token, "atlas", "old");
        generations.record(token, "atlas", "new");

        assertEquals(Map.of("atlas", "new"), generations.activate(token));
    }

    @Test
    void abortDiscardsOnlyThePendingGeneration() {
        AtlasReloadGeneration<String, String> generations = new AtlasReloadGeneration<>();
        AtlasReloadGeneration.Token token = generations.currentToken();
        generations.record(token, "atlas", "discarded");
        generations.abort(token);

        assertTrue(generations.activate(token).isEmpty());
        assertTrue(generations.activate(generations.currentToken()).isEmpty());
    }
}
