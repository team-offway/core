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
    /**
     * 목표월 <b>이후</b> 자료를 이미 가진 지역들 — 이들은 외부를 아예 안 부른다(#337).
     *
     * <p>{@code >=} 인 이유는 지역마다 발행월이 다르기 때문이다. 목표월과 같은지만 보면, 목표월보다
     * 앞선 달을 이미 받아 둔 지역이 매번 다시 대상이 된다.
     *
     * <p>{@code base_ym} 은 {@code yyyyMM} 로 0 을 채운 6자 문자열이라 사전순 비교가 곧 시간순이다.
     */
    @Query("SELECT DISTINCT h.regionId FROM HubAttraction h WHERE h.baseYm >= :baseYm")
    List<Long> findRegionIdsWithBaseYmAtLeast(@Param("baseYm") String baseYm);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HubAttraction h where h.regionId = :regionId")
    void deleteByRegionId(@Param("regionId") Long regionId);
}
