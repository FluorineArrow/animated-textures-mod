package com.animatedtextures.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic identity-token lifecycle for atlas data captured during one reload.
 */
final class AtlasReloadGeneration<K, V> {

    static final class Token {
        private Token() {
        }
    }

    private Token pending = new Token();
    private final Map<K, V> pendingValues = new LinkedHashMap<>();

    synchronized Token currentToken() {
        return pending;
    }

    synchronized void record(Token token, K key, V value) {
        if (token == pending) {
            pendingValues.put(key, value);
        }
    }

    synchronized Map<K, V> activate(Token token) {
        if (token != pending) {
            return Map.of();
        }
        Map<K, V> activated = Map.copyOf(pendingValues);
        pendingValues.clear();
        pending = new Token();
        return activated;
    }

    synchronized void abort(Token token) {
        if (token == pending) {
            pendingValues.clear();
            pending = new Token();
        }
    }
}
