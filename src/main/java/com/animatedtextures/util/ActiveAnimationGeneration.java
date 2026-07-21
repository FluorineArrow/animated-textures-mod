package com.animatedtextures.util;

import java.util.Map;

/**
 * One atomically published animation snapshot and its exact atlas bindings.
 */
record ActiveAnimationGeneration<K, V>(
        long reloadSequence,
        AnimatedTextureRegistrySnapshot snapshot,
        Map<K, V> bindings
) {
    ActiveAnimationGeneration {
        bindings = Map.copyOf(bindings);
    }
}
