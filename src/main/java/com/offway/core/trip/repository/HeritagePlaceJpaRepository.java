package com.offway.core.trip.repository;

import com.offway.core.trip.domain.HeritageGroup;
import com.offway.core.trip.domain.HeritagePlace;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeritagePlaceJpaRepository extends JpaRepository<HeritagePlace, Long> {

    List<HeritagePlace> findByRegionIdAndGroupInOrderByIdAsc(
            long regionId, Collection<HeritageGroup> groups, Pageable pageable);
}
