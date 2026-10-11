package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.ResponseResource;
import org.growforge.jalmarg.twin.model.TwinEnums.ResourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResponseResourceRepository extends JpaRepository<ResponseResource, Long> {
    Optional<ResponseResource> findByResourceCode(String resourceCode);
    List<ResponseResource> findByStatus(ResourceStatus status);
}
