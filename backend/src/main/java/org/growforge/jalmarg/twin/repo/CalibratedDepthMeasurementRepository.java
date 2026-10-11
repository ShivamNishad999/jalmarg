package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.CalibratedDepthMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalibratedDepthMeasurementRepository extends JpaRepository<CalibratedDepthMeasurement, Long> {
    Optional<CalibratedDepthMeasurement> findByMeasurementCode(String measurementCode);
    List<CalibratedDepthMeasurement> findAllByOrderByMeasurementTimestampDesc();
    List<CalibratedDepthMeasurement> findByEvidenceStatusOrderByMeasurementTimestampDesc(String evidenceStatus);
    long countByEvidenceStatus(String evidenceStatus);
}
