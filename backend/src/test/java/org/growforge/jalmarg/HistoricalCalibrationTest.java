package org.growforge.jalmarg;

import org.growforge.jalmarg.calibration.HistoricalCalibrationService;
import org.growforge.jalmarg.calibration.HistoricalFloodIncident;
import org.growforge.jalmarg.service.CopernicusDemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalCalibrationTest {

    private HistoricalCalibrationService calibrationService;

    @BeforeEach
    void setUp() {
        CopernicusDemService demService = new CopernicusDemService(true);
        calibrationService = new HistoricalCalibrationService(demService);
    }

    @Test
    void historicalIncidentsHaveVerifiedCitationsAndQualitativeFlags() {
        List<HistoricalFloodIncident> list = calibrationService.getAllBenchmarks();
        assertEquals(4, list.size(), "Must contain exactly 4 documented Delhi historical benchmark events");

        for (HistoricalFloodIncident incident : list) {
            assertNotNull(incident.incidentId());
            assertNotNull(incident.sourceAgency());
            assertNotNull(incident.sourceCitationUrl());
            assertTrue(incident.sourceCitationUrl().startsWith("http"), "Must have verifiable primary URL citation");
            assertEquals("MEASURED_AWS_TELEMETRY", incident.rainfallMeasurementType(), "Rainfall must be measured station telemetry");
            assertEquals("QUALITATIVE_MUNICIPAL_ESTIMATE", incident.waterDepthMeasurementType(),
                    "Visual depth observations must be flagged as qualitative estimates");
            assertFalse(incident.isCalibratedGroundTruth(),
                    "Qualitative estimates must be excluded from quantitative calibration ground truth");
        }
    }

    @Test
    void verifyMintoBridgeBenchmarkContext() {
        HistoricalFloodIncident minto = calibrationService.getBenchmarkById("HIST-DEL-20200719-01").orElse(null);
        assertNotNull(minto);
        assertEquals("Minto Bridge Railway Underpass", minto.locationName());
        assertEquals(74.8, minto.observedRainfallMm());
        assertEquals("IMD Safdarjung AWS (Station 42182)", minto.rainfallStation());
        assertTrue(minto.isUnderpassStructure());
        assertEquals(211.08, minto.copernicusDemElevationMetersAmsl(), 0.1);
    }
}
