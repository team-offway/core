package com.offway.core.trip.repository;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceKind;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicensedPlaceJpaRepository extends JpaRepository<LicensedPlace, Long> {

    List<LicensedPlace> findByRegionIdAndKind(long regionId, PlaceKind kind);
}
