package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.GroundTruthObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroundTruthObservationRepository extends JpaRepository<GroundTruthObservation, Long> {

    Optional<GroundTruthObservation> findByObservationId(String observationId);

    List<GroundTruthObservation> findAllByOrderByObservationTimestampDesc();

    List<GroundTruthObservation> findByWaterloggingStatusOrderByObservationTimestampDesc(String waterloggingStatus);

    List<GroundTruthObservation> findByVerificationStatusOrderByObservationTimestampDesc(String verificationStatus);

    List<GroundTruthObservation> findByReviewQueueStatusOrderByObservationTimestampDesc(String reviewQueueStatus);

    List<GroundTruthObservation> findByCorridorNameIgnoreCase(String corridorName);

    long countByWaterloggingStatus(String waterloggingStatus);

    long countByVerificationStatus(String verificationStatus);

    long countByReviewQueueStatus(String reviewQueueStatus);
}
