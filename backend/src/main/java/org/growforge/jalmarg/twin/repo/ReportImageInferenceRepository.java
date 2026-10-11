package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.ReportImageInference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportImageInferenceRepository extends JpaRepository<ReportImageInference, Long> {

    List<ReportImageInference> findByReportIdOrderByCreatedAtDesc(Long reportId);

    Optional<ReportImageInference> findTopByReportIdOrderByCreatedAtDesc(Long reportId);

    long countByPrediction(String prediction);
}
