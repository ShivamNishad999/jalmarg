package org.growforge.jalmarg.repo;

import org.growforge.jalmarg.model.Enums.RiskLevel;
import org.growforge.jalmarg.model.FloodRiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FloodRiskAssessmentRepository extends JpaRepository<FloodRiskAssessment, Long> {

    Optional<FloodRiskAssessment> findTopByHotspotIdOrderByAssessedAtDesc(Long hotspotId);

    List<FloodRiskAssessment> findByExpiresAtAfterOrderByRiskScoreDesc(Instant now);

    List<FloodRiskAssessment> findByRiskLevelAndExpiresAtAfter(RiskLevel riskLevel, Instant now);

    void deleteByExpiresAtBefore(Instant cutoff);
}
