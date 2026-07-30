package com.bionote.agent.fit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class PointExtractorTimeTest {
    @Test
    void parsesClockTimesToMinutes() {
        PointExtractor extractor = new PointExtractor(null, null, null);
        assertEquals(8 * 60 + 30, extractor.parseTimeToMinutes("8:30"), 1e-9);
        assertEquals(14 * 60 + 5, extractor.parseTimeToMinutes("14:05:00"), 1e-9);
        assertNotNull(extractor.parseTimeToMinutes("0.5")); // excel half-day → 720 min
        assertEquals(12 * 60, extractor.parseTimeToMinutes("0.5"), 1e-6);
    }
}
