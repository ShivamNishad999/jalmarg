package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_training_readiness", indexes = {
        @Index(name = "idx_ml_readiness_time", columnList = "assessmentTimestamp")
})
public class MlTrainingReadiness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant assessmentTimestamp = Instant.now();

    @Column(nullable = false, length = 64)
    private String readinessGate;

    @Column(nullable = false)
    private Integer usablePositiveEvents;

    @Column(nullable = false)
    private Integer usableNegativeObservations;

    @Column(nullable = false)
    private Integer monsoonSeasonsCovered;

    @Column(length = 64)
    private String classImbalanceRatio;

    @Column(length = 64)
    private String depthMeasurementAvailability;

    @Column(length = 64)
    private String targetLeadTimesSupported = "1h, 3h, 6h";

    @Column(nullable = false)
    private Integer targetLeakageRisksFound = 0;

    @Column(columnDefinition = "TEXT")
    private String summaryNotes;

    @Column(columnDefinition = "TEXT")
    private String missingRequirementsJson;

    @Column(columnDefinition = "TEXT")
    private String gapReportJson;

    @Column(length = 64)
    private String assessedBy = "SYSTEM_AUDITOR";

    public MlTrainingReadiness() {}

    public Long getId() { return id; }
    public Instant getAssessmentTimestamp() { return assessmentTimestamp; }
    public void setAssessmentTimestamp(Instant assessmentTimestamp) { this.assessmentTimestamp = assessmentTimestamp; }
    public String getReadinessGate() { return readinessGate; }
    public void setReadinessGate(String readinessGate) { this.readinessGate = readinessGate; }
    public Integer getUsablePositiveEvents() { return usablePositiveEvents; }
    public void setUsablePositiveEvents(Integer usablePositiveEvents) { this.usablePositiveEvents = usablePositiveEvents; }
    public Integer getUsableNegativeObservations() { return usableNegativeObservations; }
    public void setUsableNegativeObservations(Integer usableNegativeObservations) { this.usableNegativeObservations = usableNegativeObservations; }
    public Integer getMonsoonSeasonsCovered() { return monsoonSeasonsCovered; }
    public void setMonsoonSeasonsCovered(Integer monsoonSeasonsCovered) { this.monsoonSeasonsCovered = monsoonSeasonsCovered; }
    public String getClassImbalanceRatio() { return classImbalanceRatio; }
    public void setClassImbalanceRatio(String classImbalanceRatio) { this.classImbalanceRatio = classImbalanceRatio; }
    public String getDepthMeasurementAvailability() { return depthMeasurementAvailability; }
    public void setDepthMeasurementAvailability(String depthMeasurementAvailability) { this.depthMeasurementAvailability = depthMeasurementAvailability; }
    public String getTargetLeadTimesSupported() { return targetLeadTimesSupported; }
    public void setTargetLeadTimesSupported(String targetLeadTimesSupported) { this.targetLeadTimesSupported = targetLeadTimesSupported; }
    public Integer getTargetLeakageRisksFound() { return targetLeakageRisksFound; }
    public void setTargetLeakageRisksFound(Integer targetLeakageRisksFound) { this.targetLeakageRisksFound = targetLeakageRisksFound; }
    public String getSummaryNotes() { return summaryNotes; }
    public void setSummaryNotes(String summaryNotes) { this.summaryNotes = summaryNotes; }
    public String getMissingRequirementsJson() { return missingRequirementsJson; }
    public void setMissingRequirementsJson(String missingRequirementsJson) { this.missingRequirementsJson = missingRequirementsJson; }
    public String getGapReportJson() { return gapReportJson; }
    public void setGapReportJson(String gapReportJson) { this.gapReportJson = gapReportJson; }
    public String getAssessedBy() { return assessedBy; }
    public void setAssessedBy(String assessedBy) { this.assessedBy = assessedBy; }
}
