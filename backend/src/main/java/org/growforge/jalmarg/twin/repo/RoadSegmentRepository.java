package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.RoadSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadSegmentRepository extends JpaRepository<RoadSegment, Long> {
    Optional<RoadSegment> findBySegmentCode(String segmentCode);
    List<RoadSegment> findByIsUnderpassTrue();
    List<RoadSegment> findByZoneId(Long zoneId);
}
