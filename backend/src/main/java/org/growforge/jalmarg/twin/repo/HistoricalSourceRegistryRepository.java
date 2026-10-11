package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.HistoricalSourceRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistoricalSourceRegistryRepository extends JpaRepository<HistoricalSourceRegistry, Long> {

    Optional<HistoricalSourceRegistry> findBySourceCode(String sourceCode);

    List<HistoricalSourceRegistry> findAllByOrderByCreatedAtDesc();

    List<HistoricalSourceRegistry> findByAcquisitionStatus(String acquisitionStatus);
}
