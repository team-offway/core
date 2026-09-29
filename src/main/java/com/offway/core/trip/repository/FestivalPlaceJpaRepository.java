package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPlace;
import java.time.LocalDate;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface FestivalPlaceJpaRepository extends JpaRepository<FestivalPlace, Long> {

    /**
     * 그날 열리는 축제 — 시작일·종료일 <b>당일을 포함</b>한다.
     *
     * <p>정렬을 시작일로 둔다. 여러 개가 겹치면 먼저 시작한 쪽이 그 지역에서 더 오래 이어지는 행사일
     * 때가 많고, 무엇보다 순서가 매번 같아야 같은 요청이 같은 코스를 낸다.
     */
    @Query("""
            select festival from FestivalPlace festival
            where festival.regionId = :regionId
              and festival.eventStart <= :date
              and festival.eventEnd >= :date
            order by festival.eventStart asc, festival.id asc
            """)
    List<FestivalPlace> findOpenOn(
            @Param("regionId") long regionId, @Param("date") LocalDate date, Pageable pageable);

    /**
     * 그날 이후에 <b>열릴</b> 축제 — 아직 시작하지 않은 것만(#622).
     *
     * <p>{@code eventStart > date} 다. 그날 진행 중인 축제는 {@link #findOpenOn} 이 코스에 넣으므로
     * 여기서 또 내보내면 같은 것을 두 번 말한다.
     *
     * <p>시작일 오름차순 — 가까운 것부터 권하는 것이 자연스럽고, 순서가 고정이라 같은 요청이 같은
     * 제안을 낸다.
     */
    @Query("""
            select festival from FestivalPlace festival
            where festival.regionId = :regionId
              and festival.eventStart > :date
            order by festival.eventStart asc, festival.id asc
            """)
    List<FestivalPlace> findUpcomingAfter(
            @Param("regionId") long regionId, @Param("date") LocalDate date, Pageable pageable);

    /** 식별자 여럿을 한 번에 — 슬롯마다 읽으면 N+1 이다(#622). */
    @Query("select festival from FestivalPlace festival where festival.id in :ids")
    List<FestivalPlace> findAllByIdIn(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from FestivalPlace festival where festival.fetchedAt < :fetchedAt")
    int deleteByFetchedAtBefore(@Param("fetchedAt") LocalDateTime fetchedAt);
}
