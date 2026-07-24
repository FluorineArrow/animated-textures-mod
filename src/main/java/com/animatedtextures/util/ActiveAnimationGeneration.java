package com.animatedtextures.util;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * One atomically published animation snapshot and its exact atlas bindings.
 */
record ActiveAnimationGeneration<K, V>(
        long reloadSequence,
        AnimatedTextureRegistrySnapshot snapshot,
        Map<K, V> bindings,
        PreparedFrameCache frameCache,
        AnimationFrameScheduler frameScheduler
) {
    ActiveAnimationGeneration {
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }
}
