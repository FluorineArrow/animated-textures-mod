package com.animatedtextures.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationFrameSchedulerTest {

    @Test
    void samplesA144HzRenderLoopAtSixtyHz() {
        AnimationFrameScheduler scheduler = new AnimationFrameScheduler();
        long now = 0;
        int updates = 0;
        long elapsedMs = 0;
        scheduler.pollElapsedMillis(now);
        for (int frame = 0; frame < 144; frame++) {
            now += 1_000_000_000L / 144;
            long elapsed = scheduler.pollElapsedMillis(now);
            if (elapsed > 0) {
                updates++;
                elapsedMs += elapsed;
            }
        }

        assertEquals(59, updates);
        assertTrue(elapsedMs >= 985 && elapsedMs <= 990);
        assertTrue(scheduler.pollElapsedMillis(now + 7_000_000L) > 0);
    }

    @Test
    void longPauseProducesOneCatchUpUpdate() {
        AnimationFrameScheduler scheduler = new AnimationFrameScheduler();
        scheduler.pollElapsedMillis(10);

        assertEquals(5_000, scheduler.pollElapsedMillis(5_000_000_010L));
        assertEquals(0, scheduler.pollElapsedMillis(5_001_000_010L));
    }

    @Test
    void oneNanosecondIntervalSupportsTickDrivenTiming() {
        AnimationFrameScheduler scheduler = new AnimationFrameScheduler(1);
        scheduler.pollElapsedMillis(0);

        assertEquals(50, scheduler.pollElapsedMillis(50_000_000L));
    }
}
