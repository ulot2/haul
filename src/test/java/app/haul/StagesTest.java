package app.haul;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StagesTest {
    @Test
    public void addsUpEachStage() {
        Stages s = new Stages(1_000);
        s.on(Engine.Progress.READING, 3_100);     // 2.1 s starting
        s.on(0, 6_500);                            // 3.4 s reading
        s.on(40, 8_000);
        s.on(100, 12_000);
        s.on(Engine.Progress.WORKING, 13_000);    // 6.5 s downloading
        s.saving(13_020);                          // 0.02 s making: too short to show
        assertEquals("12.4 s: start 2.1 · read 3.4 · get 6.5 · save 0.4", s.done(13_420));
    }

    @Test
    public void aSecondTryCountsToo() {
        Stages s = new Stages(0);
        s.on(Engine.Progress.READING, 1_000);
        s.on(Engine.Progress.STARTING, 2_000);    // failed, the engine updated, trying again
        s.on(Engine.Progress.READING, 4_000);
        assertEquals("5.0 s: start 3.0 · read 2.0", s.done(5_000));
    }
}
