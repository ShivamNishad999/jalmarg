package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.TwinEnums.TwinSnapshotType;
import org.growforge.jalmarg.twin.model.TwinSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TwinSnapshotRepository extends JpaRepository<TwinSnapshot, Long> {
    List<TwinSnapshot> findBySnapshotTypeOrderBySnapshotTimestampDesc(TwinSnapshotType type);
    Optional<TwinSnapshot> findTopBySnapshotTypeOrderBySnapshotTimestampDesc(TwinSnapshotType type);
    List<TwinSnapshot> findByZoneIdOrderBySnapshotTimestampDesc(Long zoneId);
}
