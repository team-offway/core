package com.offway.core.trip.repository;

import com.offway.core.trip.domain.PetFriendlyPlace;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface PetFriendlyPlaceJpaRepository extends JpaRepository<PetFriendlyPlace, Long> {

    /** 그 지역의 반려동반 장소. id 순이다 — 순서가 매번 같아야 같은 요청이 같은 응답을 낸다. */
    List<PetFriendlyPlace> findByRegionIdOrderByIdAsc(long regionId);

    @Modifying
    @Query("delete from PetFriendlyPlace pet where pet.fetchedAt < :fetchedAt")
    int deleteByFetchedAtBefore(@Param("fetchedAt") LocalDateTime fetchedAt);
}
