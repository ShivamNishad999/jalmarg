package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.DataSourceRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataSourceRegistryRepository extends JpaRepository<DataSourceRegistry, Long> {
    Optional<DataSourceRegistry> findBySourceCode(String sourceCode);
}
