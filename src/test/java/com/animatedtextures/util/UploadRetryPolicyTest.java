package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadRetryPolicyTest {

    @Test
    void retriesExponentiallyAndCapsDelay() {
        UploadRetryPolicy policy = new UploadRetryPolicy(4);

        assertTrue(policy.isDue(0));
        assertEquals(1, policy.recordFailure(0));
        assertFalse(policy.isDue(0));
        assertTrue(policy.isDue(1));
        assertEquals(2, policy.recordFailure(1));
        assertFalse(policy.isDue(2));
        assertTrue(policy.isDue(3));
        assertEquals(4, policy.recordFailure(3));
        assertEquals(4, policy.recordFailure(7));
    }

    @Test
    void changingRevisionCannotBypassBindingCooldown() {
        UploadRetryPolicy policy = new UploadRetryPolicy(100);
        int attempts = 0;
        for (long tick = 1; tick <= 20; tick++) {
            if (policy.isDue(tick)) {
                attempts++;
                policy.recordFailure(tick);
            }
        }

        assertEquals(5, attempts);
    }

    @Test
    void successResetsState() {
        UploadRetryPolicy policy = new UploadRetryPolicy(100);
        policy.recordFailure(10);

        assertEquals(1, policy.recordSuccess());
        assertEquals(0, policy.failures());
        assertTrue(policy.isDue(10));
    }

    @Test
    void deadlineComparisonSurvivesSignedLongWrap() {
        UploadRetryPolicy policy = new UploadRetryPolicy(4);
        policy.recordFailure(Long.MAX_VALUE);

        assertFalse(policy.isDue(Long.MAX_VALUE));
        assertTrue(policy.isDue(Long.MIN_VALUE));
    }
}
