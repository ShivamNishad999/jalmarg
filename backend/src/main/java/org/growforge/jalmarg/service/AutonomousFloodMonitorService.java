package org.growforge.jalmarg.service;

import org.growforge.jalmarg.dto.Dtos.FloodRiskStatusView;
import org.growforge.jalmarg.model.Enums.RiskLevel;
import org.growforge.jalmarg.model.Enums.RiskStatus;
import org.growforge.jalmarg.model.FloodRiskAssessment;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.repo.FloodRiskAssessmentRepository;
import org.growforge.jalmarg.repo.HotspotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Autonomous Flood Risk Monitoring Worker.
 * Periodically evaluates multi-factor flood risk across all authoritative
 * monitored hotspots in Delhi NCR on a 15–30 minute cycle.
 */
@Service
public class AutonomousFloodMonitorService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousFloodMonitorService.class);

    private final HotspotRepository hotspotRepo;
    private final FloodRiskAssessmentService riskService;
    private final FloodRiskAssessmentRepository riskRepo;
    private final AlertService alertService;
    private final boolean enabled;
    private final int batchSize;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private int totalCycles = 0;
    private Instant lastRunAt = null;
    private long lastDurationMs = 0;
    private int lastEvaluatedCount = 0;
    private int lastCriticalCount = 0;
    private int lastHighCount = 0;
    private int lastAlertsGeneratedCount = 0;

    public AutonomousFloodMonitorService(HotspotRepository hotspotRepo,
                                         FloodRiskAssessmentService riskService,
                                         FloodRiskAssessmentRepository riskRepo,
                                         AlertService alertService,
                                         @Value("${jalmarg.monitor.enabled:true}") boolean enabled,
                                         @Value("${jalmarg.monitor.batch-size:5}") int batchSize) {
        this.hotspotRepo = hotspotRepo;
        this.riskService = riskService;
        this.riskRepo = riskRepo;
        this.alertService = alertService;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(20, batchSize));
        log.info("Autonomous Flood Risk Monitor: {}", enabled ? "enabled" : "disabled");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Scheduled background cycle running every 15 minutes by default.
     */
    @Scheduled(fixedDelayString = "${jalmarg.monitor.interval-ms:900000}", initialDelay = 15000)
    public void scheduledRun() {
        if (!enabled) return;
        runEvaluationCycle();
    }

    /**
     * Runs an autonomous multi-hotspot evaluation cycle.
     */
    @Transactional
    public List<FloodRiskAssessment> runEvaluationCycle() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Autonomous flood evaluation cycle already in progress — skipping concurrent trigger.");
            return List.of();
        }

        long startTime = System.currentTimeMillis();
        List<FloodRiskAssessment> results = new ArrayList<>();
        int critical = 0;
        int high = 0;
        int alertsGen = 0;

        try {
            List<Hotspot> hotspots = hotspotRepo.findAll();
            if (hotspots.isEmpty()) {
                log.info("No authoritative hotspots found for autonomous evaluation.");
                return List.of();
            }

            log.info("Starting autonomous flood risk evaluation cycle across {} hotspots...", hotspots.size());

            // Process in sequential batches with rate-limiting pacing
            for (int i = 0; i < hotspots.size(); i += batchSize) {
                int end = Math.min(hotspots.size(), i + batchSize);
                List<Hotspot> batch = hotspots.subList(i, end);

                for (Hotspot h : batch) {
                    try {
                        FloodRiskAssessment assessment = riskService.assess(
                                h.getLatitude(), h.getLongitude(), h.getArea(), h.getId(), h.getName());
                        results.add(assessment);

                        if (assessment.getRiskLevel() == RiskLevel.CRITICAL) {
                            critical++;
                        } else if (assessment.getRiskLevel() == RiskLevel.HIGH) {
                            high++;
                        }
                    } catch (Exception e) {
                        log.warn("Error assessing risk for hotspot #{}: {}", h.getId(), e.getMessage());
                    }
                }

                // Small pacing delay between batches
                if (end < hotspots.size()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            // Cleanup expired historical assessments older than 24 hours
            try {
                riskRepo.deleteByExpiresAtBefore(Instant.now().minus(java.time.Duration.ofHours(24)));
            } catch (Exception e) {
                log.warn("Error cleaning up expired risk assessments: {}", e.getMessage());
            }

            lastDurationMs = System.currentTimeMillis() - startTime;
            lastRunAt = Instant.now();
            lastEvaluatedCount = results.size();
            lastCriticalCount = critical;
            lastHighCount = high;
            lastAlertsGeneratedCount = alertsGen;
            totalCycles++;

            log.info("Autonomous flood monitor cycle finished in {}ms: {} assessed, {} critical, {} high.",
                    lastDurationMs, results.size(), critical, high);

        } finally {
            isRunning.set(false);
        }

        return results;
    }

    public FloodRiskStatusView getStatus() {
        return new FloodRiskStatusView(
                isRunning.get(),
                totalCycles,
                lastRunAt,
                lastDurationMs,
                lastEvaluatedCount,
                lastCriticalCount,
                lastHighCount,
                lastAlertsGeneratedCount,
                riskService.getAssessmentTtlMinutes() + " minutes"
        );
    }
}
