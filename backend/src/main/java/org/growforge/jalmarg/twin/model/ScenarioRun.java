package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Persists what-if scenario simulations with explicit inputs, assumptions, and deltas.
 * Strictly isolated from live operational records and live routing.
 */
@Entity
@Table(name = "twin_scenario_runs", indexes = {
        @Index(name = "idx_scenario_created", columnList = "createdAt"),
        @Index(name = "idx_scenario_zone", columnList = "targetZoneId")
})
public class ScenarioRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String scenarioName;

    @Column(nullable = false, length = 64)
    private String runType; // WHAT_IF_SIMULATION

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    // Simulation Assumptions & Inputs
    private double rainfallTotalMm;
    private double rainfallIntensityMmPerHour;
    private int durationHours;

    private Long targetZoneId;

    @Column(length = 128)
    private String targetZoneName;

    /** Drainage capacity assumption (0-100%, labeled as explicit assumption) */
    private Double assumedDrainageCapacityPercent;

    @Column(columnDefinition = "TEXT")
    private String roadClosureAssumptionsJson;

    @Column(columnDefinition = "TEXT")
    private String resourceAvailabilityAssumptionsJson;

    @Column(nullable = false, length = 64)
    private String simulationLabel; // SIMULATION / TEST DATA

    // Output Outcomes & Deltas
    private int affectedRoadsCount;
    private int baselineRiskScore;
    private int simulatedRiskScore;
    private int deltaRiskScore;

    @Column(columnDefinition = "TEXT")
    private String factorBreakdownJson;

    @Column(columnDefinition = "TEXT")
    private String dataGapsJson;

    @Column(nullable = false, length = 64)
    private String modelVersion;

    @Column(nullable = false, length = 32)
    private String status; // COMPLETED, FAILED

    public ScenarioRun() {}

    public ScenarioRun(String scenarioName, String runType, String createdBy, double rainfallTotalMm,
                       double rainfallIntensityMmPerHour, int durationHours, Long targetZoneId,
                       String targetZoneName, Double assumedDrainageCapacityPercent,
                       String roadClosureAssumptionsJson, String resourceAvailabilityAssumptionsJson,
                       int affectedRoadsCount, int baselineRiskScore, int simulatedRiskScore,
                       int deltaRiskScore, String factorBreakdownJson, String dataGapsJson,
                       String modelVersion, String status) {
        this.scenarioName = scenarioName;
        this.runType = runType;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.rainfallTotalMm = rainfallTotalMm;
        this.rainfallIntensityMmPerHour = rainfallIntensityMmPerHour;
        this.durationHours = durationHours;
        this.targetZoneId = targetZoneId;
        this.targetZoneName = targetZoneName;
        this.assumedDrainageCapacityPercent = assumedDrainageCapacityPercent;
        this.roadClosureAssumptionsJson = roadClosureAssumptionsJson;
        this.resourceAvailabilityAssumptionsJson = resourceAvailabilityAssumptionsJson;
        this.simulationLabel = "SIMULATION / TEST DATA";
        this.affectedRoadsCount = affectedRoadsCount;
        this.baselineRiskScore = baselineRiskScore;
        this.simulatedRiskScore = simulatedRiskScore;
        this.deltaRiskScore = deltaRiskScore;
        this.factorBreakdownJson = factorBreakdownJson;
        this.dataGapsJson = dataGapsJson;
        this.modelVersion = modelVersion;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }

    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public double getRainfallTotalMm() { return rainfallTotalMm; }
    public void setRainfallTotalMm(double rainfallTotalMm) { this.rainfallTotalMm = rainfallTotalMm; }

    public double getRainfallIntensityMmPerHour() { return rainfallIntensityMmPerHour; }
    public void setRainfallIntensityMmPerHour(double rainfallIntensityMmPerHour) { this.rainfallIntensityMmPerHour = rainfallIntensityMmPerHour; }

    public int getDurationHours() { return durationHours; }
    public void setDurationHours(int durationHours) { this.durationHours = durationHours; }

    public Long getTargetZoneId() { return targetZoneId; }
    public void setTargetZoneId(Long targetZoneId) { this.targetZoneId = targetZoneId; }

    public String getTargetZoneName() { return targetZoneName; }
    public void setTargetZoneName(String targetZoneName) { this.targetZoneName = targetZoneName; }

    public Double getAssumedDrainageCapacityPercent() { return assumedDrainageCapacityPercent; }
    public void setAssumedDrainageCapacityPercent(Double assumedDrainageCapacityPercent) { this.assumedDrainageCapacityPercent = assumedDrainageCapacityPercent; }

    public String getRoadClosureAssumptionsJson() { return roadClosureAssumptionsJson; }
    public void setRoadClosureAssumptionsJson(String roadClosureAssumptionsJson) { this.roadClosureAssumptionsJson = roadClosureAssumptionsJson; }

    public String getResourceAvailabilityAssumptionsJson() { return resourceAvailabilityAssumptionsJson; }
    public void setResourceAvailabilityAssumptionsJson(String resourceAvailabilityAssumptionsJson) { this.resourceAvailabilityAssumptionsJson = resourceAvailabilityAssumptionsJson; }

    public String getSimulationLabel() { return simulationLabel; }
    public void setSimulationLabel(String simulationLabel) { this.simulationLabel = simulationLabel; }

    public int getAffectedRoadsCount() { return affectedRoadsCount; }
    public void setAffectedRoadsCount(int affectedRoadsCount) { this.affectedRoadsCount = affectedRoadsCount; }

    public int getBaselineRiskScore() { return baselineRiskScore; }
    public void setBaselineRiskScore(int baselineRiskScore) { this.baselineRiskScore = baselineRiskScore; }

    public int getSimulatedRiskScore() { return simulatedRiskScore; }
    public void setSimulatedRiskScore(int simulatedRiskScore) { this.simulatedRiskScore = simulatedRiskScore; }

    public int getDeltaRiskScore() { return deltaRiskScore; }
    public void setDeltaRiskScore(int deltaRiskScore) { this.deltaRiskScore = deltaRiskScore; }

    public String getFactorBreakdownJson() { return factorBreakdownJson; }
    public void setFactorBreakdownJson(String factorBreakdownJson) { this.factorBreakdownJson = factorBreakdownJson; }

    public String getDataGapsJson() { return dataGapsJson; }
    public void setDataGapsJson(String dataGapsJson) { this.dataGapsJson = dataGapsJson; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
