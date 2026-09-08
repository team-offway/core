package com.offway.core.trip.repository;

import com.offway.core.trip.domain.CampingPlace;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CampingPlaceJpaRepository extends JpaRepository<CampingPlace, Long> {

    /**
     * 그 지역의 야영장 — <b>사진 있는 것을 먼저</b>.
     *
     * <p>{@code CASE} 로 사진 유무를 0·1 로 접어 정렬 키로 쓴다. 그다음은 id 순이다 — 순서가 매번
     * 같아야 같은 요청이 같은 코스를 낸다.
     */
    @Query("""
            select camping from CampingPlace camping
            where camping.regionId = :regionId
            order by case when camping.imageUrl is null or camping.imageUrl = '' then 1 else 0 end asc,
                     camping.id asc
            """)
    List<CampingPlace> findCandidates(@Param("regionId") long regionId, Pageable pageable);

    @Modifying
    @Query("delete from CampingPlace camping where camping.fetchedAt < :fetchedAt")
    int deleteByFetchedAtBefore(@Param("fetchedAt") LocalDateTime fetchedAt);
}
