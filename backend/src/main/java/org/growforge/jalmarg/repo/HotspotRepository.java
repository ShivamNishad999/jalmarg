package org.growforge.jalmarg.repo;

import org.growforge.jalmarg.model.Hotspot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotspotRepository extends JpaRepository<Hotspot, Long> {

    /** Rough bounding-box pre-filter; exact distance is checked in Java. */
    List<Hotspot> findByLatitudeBetweenAndLongitudeBetween(double latMin, double latMax,
                                                           double lngMin, double lngMax);

    List<Hotspot> findAllByOrderByTotalReportsDesc();
}
