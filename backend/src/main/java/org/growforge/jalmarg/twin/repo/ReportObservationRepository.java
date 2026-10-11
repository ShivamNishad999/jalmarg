package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.ReportObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportObservationRepository extends JpaRepository<ReportObservation, Long> {
    List<ReportObservation> findByReportIdOrderBySubmittedAtAsc(Long reportId);
}
