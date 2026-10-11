package org.growforge.jalmarg;

import org.growforge.jalmarg.model.Enums.DepthLevel;
import org.growforge.jalmarg.model.Enums.DepthSource;
import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.service.Geo;
import org.growforge.jalmarg.service.HotspotService;
import org.growforge.jalmarg.service.SafetyRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fast unit tests for the pure logic (no Spring context, no AWS). */
class LogicTest {

    @Test
    void severityThresholds() {
        assertEquals(Severity.LOW, SafetyRules.severityFor(0.1));
        assertEquals(Severity.MODERATE, SafetyRules.severityFor(0.5));
        assertEquals(Severity.HIGH, SafetyRules.severityFor(1.0));
        assertEquals(Severity.SEVERE, SafetyRules.severityFor(2.0));
    }

    @Test
    void vehicleSafety() {
        Report r = new Report();
        r.setDepthFt(DepthLevel.SHIN.feet);
        SafetyRules.apply(r);
        assertFalse(r.isBikeSafe());
        assertFalse(r.isCarSafe());
        assertTrue(r.isWalkSafe());
    }

    @Test
    void distances() {
        // Minto Bridge to ITO is roughly 1.6 km
        double d = Geo.distanceM(28.6347, 77.2263, 28.6289, 77.2410);
        assertTrue(d > 1300 && d < 1800, "got " + d);
        // point on the segment is ~0 m away
        assertTrue(Geo.pointToSegmentM(28.5, 77.25, 28.5, 77.2, 28.5, 77.3) < 1);
    }

    @Test
    void areaGuess() {
        assertEquals("Delhi", HotspotService.guessArea(28.6347, 77.2263));
        assertEquals("Noida", HotspotService.guessArea(28.5700, 77.3260));
        assertEquals("Greater Noida", HotspotService.guessArea(28.4625, 77.4905));
        assertEquals("Gurugram", HotspotService.guessArea(28.4296, 77.0118));
        assertEquals("Ghaziabad", HotspotService.guessArea(28.6780, 77.4400));
    }

    @Test
    void demoSourceExists() {
        assertNotNull(DepthSource.valueOf("DEMO"));
    }
}
