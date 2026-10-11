package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.EnvironmentalObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EnvironmentalObservationRepository extends JpaRepository<EnvironmentalObservation, Long> {
    List<EnvironmentalObservation> findByObservedAtAfterOrderByObservedAtDesc(Instant cutoff);
}
