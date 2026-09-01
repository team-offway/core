package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPeriod;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface FestivalPeriodJpaRepository extends JpaRepository<FestivalPeriod, String> {

    /**
     * 이 contentId 들의 기간을 한 번에 읽는다.
     *
     * <p><b>한 건씩 읽지 않는다.</b> 코스 후보가 수십 건이라 그러면 조회가 후보 수만큼 돈다 — 요청
     * 경로에서 도는 질의라 그 곱셈이 그대로 사용자 대기가 된다.
     */
    List<FestivalPeriod> findByContentIdIn(Collection<String> contentIds);

    /**
     * 이번 수집에 없던 행을 지운다 — <b>취소된 축제를 걷어내는 유일한 경로</b>(#388).
     *
     * <p>TourAPI 가 취소된 축제를 더 이상 안 주면 upsert 만으로는 <b>옛 행이 그대로 남는다.</b> 저장된
     * 미래 기간에는 {@code isOpenOn} 이 계속 참이라, 열리지도 않는 축제를 코스에 넣게 된다.
     *
     * <p><b>{@code minEventEnd} 로 범위를 좁힌다.</b> 이번 조회가 그 날짜 이후에 끝나는 축제만 봤으므로,
     * 그보다 앞서 끝난 옛 행까지 지우면 <b>보지도 않은 것을 없다고 단정</b>하는 셈이 된다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FestivalPeriod period "
            + "where period.eventEnd >= :minEventEnd and period.contentId not in :keptContentIds")
    int deleteMissingFrom(
            @Param("keptContentIds") Collection<String> keptContentIds,
            @Param("minEventEnd") LocalDate minEventEnd);
}
