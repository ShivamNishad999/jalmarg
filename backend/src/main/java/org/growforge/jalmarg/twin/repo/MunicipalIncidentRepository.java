package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.MunicipalIncident;
import org.growforge.jalmarg.twin.model.TwinEnums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MunicipalIncidentRepository extends JpaRepository<MunicipalIncident, Long> {
    Optional<MunicipalIncident> findByIncidentNumber(String incidentNumber);
    List<MunicipalIncident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);
    List<MunicipalIncident> findByStatusNotOrderByPriorityScoreDesc(IncidentStatus status);
    List<MunicipalIncident> findAllByOrderByCreatedAtDesc();
    long countByStatus(IncidentStatus status);
}
