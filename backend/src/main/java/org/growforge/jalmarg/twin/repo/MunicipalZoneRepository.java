package org.growforge.jalmarg.twin.repo;

import org.growforge.jalmarg.twin.model.MunicipalZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MunicipalZoneRepository extends JpaRepository<MunicipalZone, Long> {
    Optional<MunicipalZone> findByZoneCode(String zoneCode);
    List<MunicipalZone> findByMunicipalityName(String municipalityName);
}
