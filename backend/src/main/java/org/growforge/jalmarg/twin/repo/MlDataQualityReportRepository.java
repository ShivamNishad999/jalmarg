package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.MlDataQualityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MlDataQualityReportRepository extends JpaRepository<MlDataQualityReport, Long> {
    List<MlDataQualityReport> findByDatasetCodeOrderByReportTimestampDesc(String datasetCode);
    List<MlDataQualityReport> findAllByOrderByReportTimestampDesc();
}
