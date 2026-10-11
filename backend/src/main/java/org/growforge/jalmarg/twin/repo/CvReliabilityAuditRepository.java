package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.CvReliabilityAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CvReliabilityAuditRepository extends JpaRepository<CvReliabilityAudit, Long> {

    Optional<CvReliabilityAudit> findTopByOrderByAuditTimestampDesc();

    Optional<CvReliabilityAudit> findTopByModelVersionOrderByAuditTimestampDesc(String modelVersion);
}
