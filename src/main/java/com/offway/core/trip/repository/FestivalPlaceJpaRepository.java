package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPlace;
import java.time.LocalDate;
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

    @Modifying
    @Query("delete from FestivalPlace festival where festival.fetchedAt < :fetchedAt")
    int deleteByFetchedAtBefore(@Param("fetchedAt") LocalDateTime fetchedAt);
}
