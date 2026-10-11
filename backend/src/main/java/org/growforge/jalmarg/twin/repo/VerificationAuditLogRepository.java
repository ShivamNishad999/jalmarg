package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.VerificationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerificationAuditLogRepository extends JpaRepository<VerificationAuditLog, Long> {

    List<VerificationAuditLog> findByObservationIdOrderByDecidedAtDesc(String observationId);

    List<VerificationAuditLog> findAllByOrderByDecidedAtDesc();
}
