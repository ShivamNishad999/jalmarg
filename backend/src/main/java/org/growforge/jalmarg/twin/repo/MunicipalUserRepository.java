package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.MunicipalUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MunicipalUserRepository extends JpaRepository<MunicipalUser, Long> {
    Optional<MunicipalUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
}
