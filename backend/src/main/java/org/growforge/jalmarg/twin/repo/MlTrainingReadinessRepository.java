package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.MlTrainingReadiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MlTrainingReadinessRepository extends JpaRepository<MlTrainingReadiness, Long> {
    Optional<MlTrainingReadiness> findTopByOrderByAssessmentTimestampDesc();
}
