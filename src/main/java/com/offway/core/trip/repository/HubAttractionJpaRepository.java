package com.offway.core.trip.repository;

import com.offway.core.trip.domain.HubAttraction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface HubAttractionJpaRepository extends JpaRepository<HubAttraction, Long> {

    List<HubAttraction> findByRegionIdOrderByHubRankAsc(Long regionId);

    List<HubAttraction> findByRegionIdInOrderByRegionIdAscHubRankAsc(List<Long> regionIds);

    void deleteByRegionId(Long regionId);
}
