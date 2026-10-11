package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.MunicipalDryRoadInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MunicipalDryRoadInspectionRepository extends JpaRepository<MunicipalDryRoadInspection, Long> {
    Optional<MunicipalDryRoadInspection> findByInspectionCode(String inspectionCode);
    List<MunicipalDryRoadInspection> findAllByOrderByInspectionTimestampDesc();
    List<MunicipalDryRoadInspection> findByReviewStatusOrderByInspectionTimestampDesc(String reviewStatus);
    long countByReviewStatus(String reviewStatus);
}
