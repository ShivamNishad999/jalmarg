package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.DrainageAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrainageAssetRepository extends JpaRepository<DrainageAsset, Long> {
    Optional<DrainageAsset> findByAssetCode(String assetCode);
    List<DrainageAsset> findByZoneId(Long zoneId);
}
