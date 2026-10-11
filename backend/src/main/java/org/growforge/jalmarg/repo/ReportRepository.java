package org.growforge.jalmarg.repo;

import org.growforge.jalmarg.model.Enums.ReportStatus;
import org.growforge.jalmarg.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findByCreatedAtAfterOrderByCreatedAtDesc(Instant after);

    List<Report> findByHotspotIdOrderByCreatedAtDesc(Long hotspotId);

    @Query("select r from Report r where r.status = :status and r.lastConfirmedAt < :before")
    List<Report> findStale(@Param("status") ReportStatus status, @Param("before") Instant before);

    long countByStatus(ReportStatus status);
}
