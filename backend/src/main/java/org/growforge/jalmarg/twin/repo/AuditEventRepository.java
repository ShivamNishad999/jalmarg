package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop50ByOrderByEventTimestampDesc();
    List<AuditEvent> findByTargetEntityAndTargetEntityIdOrderByEventTimestampDesc(String entity, String entityId);
}
