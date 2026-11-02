package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The WiZ toggle: any light on means switch all off; the setPilot message. */
public class WizLightsTest {

    private static final String ON = "{\"method\":\"getPilot\",\"env\":\"pro\",\"result\":{\"mac\":\"cc4085976412\",\"state\":true,\"dimming\":70}}";
    private static final String OFF = "{\"method\":\"getPilot\",\"env\":\"pro\",\"result\":{\"mac\":\"444f8e3d5f18\",\"state\": false,\"dimming\":50}}";

    @Test
    public void anyLightOnCounts() {
        assertTrue(WizLights.anyOn(Arrays.asList(OFF, ON)));
        assertFalse(WizLights.anyOn(Arrays.asList(OFF, OFF)));
        assertFalse(WizLights.anyOn(Collections.emptyList()));
    }

    @Test
    public void targetIsAllOrListedMacs() {
        assertTrue(WizLights.targets("all", ON));
        assertTrue(WizLights.targets("9877d5210c6e, CC4085976412", ON));
        assertFalse(WizLights.targets("9877d5210c6e", ON));
        assertFalse(WizLights.targets("9877d5210c6e", "{\"result\":{\"state\":true}}"));
    }

    @Test
    public void lightKeysParseLikeAppKeys() {
        java.util.Map<Integer, String> scans = RemoteKeys.parseAppScancodes(
                Arrays.asList("scan:400:all", "scan:580:444f8e3d5f18,cc4085976412"));
        assertEquals("all", scans.get(400));
        assertEquals("444f8e3d5f18,cc4085976412", scans.get(580));
    }

    @Test
    public void setPilotMessage() {
        assertEquals("{\"method\":\"setPilot\",\"params\":{\"state\":false}}", WizLights.pilot(false));
        assertEquals("{\"method\":\"setPilot\",\"params\":{\"state\":true}}", WizLights.pilot(true));
    }
}
