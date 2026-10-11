package org.growforge.jalmarg.twin.service;

import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.twin.model.ReportImageInference;

import java.util.Map;

/**
 * Service interface for Computer Vision image inference and metadata.
 */
public interface VisionInferenceService {

    /**
     * Fault-tolerant inference invocation on an uploaded road photo.
     */
    ReportImageInference processReportImage(Report report, byte[] photoBytes);

    Map<String, Object> getEvaluationReport();

    Map<String, Object> getReadinessAssessment();

    Map<String, Object> getDatasetAuditReport();
}
