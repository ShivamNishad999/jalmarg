package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.RainfallCoverageManifest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RainfallCoverageManifestRepository extends JpaRepository<RainfallCoverageManifest, Long> {
    Optional<RainfallCoverageManifest> findByPeriodCode(String periodCode);
    List<RainfallCoverageManifest> findAllByOrderByStartDateAsc();
    List<RainfallCoverageManifest> findByAcquisitionStatus(String acquisitionStatus);
}
