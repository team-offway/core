package com.offway.core.trip.repository;

import com.offway.core.trip.domain.HubAttraction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface HubAttractionJpaRepository extends JpaRepository<HubAttraction, Long> {

    List<HubAttraction> findByRegionIdOrderByHubRankAsc(Long regionId);

    List<HubAttraction> findByRegionIdInOrderByRegionIdAscHubRankAsc(List<Long> regionIds);

    /**
     * 한 지역의 적재를 <b>DELETE 한 방</b>으로 지운다.
     *
     * <p>파생 삭제(`deleteByRegionId`)는 엔티티를 먼저 읽고 건마다 DELETE 를 날린다. 갱신은 89개 지역을
     * 돌고 지역마다 최대 100행이라 그 차이가 곱해진다. 라이프사이클 콜백도 연관관계도 없어 벌크로 안전하다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HubAttraction h where h.regionId = :regionId")
    void deleteByRegionId(@Param("regionId") Long regionId);

    /**
     * 그 달 이상 데이터를 가진 <b>지역 수</b>(중복 제거) — 지역마다 여러 순위가 있으므로 distinct 로 센다.
     *
     * <p>{@code base_ym} 은 {@code yyyyMM} 고정폭이라 문자열 비교가 곧 시간 비교다.
     */
    @Query("select count(distinct h.regionId) from HubAttraction h "
            + "where h.regionId in :regionIds and h.baseYm >= :baseYm")
    long countRegionsWithBaseYmAtLeast(@Param("regionIds") List<Long> regionIds, @Param("baseYm") String baseYm);
}
