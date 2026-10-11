package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.ScenarioRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScenarioRunRepository extends JpaRepository<ScenarioRun, Long> {
    List<ScenarioRun> findAllByOrderByCreatedAtDesc();
}
