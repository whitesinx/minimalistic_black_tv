package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Bigger volume steps: a one-step change carries on to the configured step, within range. */
public class VolumeStepsTest {

    @Test
    public void oneStepBecomesFive() {
        assertEquals(40, VolumeSteps.target(35, 36, 5, 100));
        assertEquals(30, VolumeSteps.target(35, 34, 5, 100));
    }

    @Test
    public void staysInRange() {
        assertEquals(100, VolumeSteps.target(98, 99, 5, 100));
        assertEquals(0, VolumeSteps.target(2, 1, 5, 100));
    }

    @Test
    public void otherChangesAreLeftAlone() {
        assertEquals(60, VolumeSteps.target(35, 60, 5, 100));   // a slider or an app setting it
        assertEquals(35, VolumeSteps.target(35, 35, 5, 100));
        assertEquals(36, VolumeSteps.target(-1, 36, 5, 100));
    }
}
