package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionVisitorDaily;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RegionVisitorDailyJpaRepository extends JpaRepository<RegionVisitorDaily, Long> {

    List<RegionVisitorDaily> findBySignguCodeInAndBaseDateBetween(
            Collection<String> signguCodes, LocalDate from, LocalDate to);

    /**
     * 그 달을 이미 받았는가 — <b>같은 달을 두 번 받지 않으려는 가드</b>.
     *
     * <p>원본은 완결된 달만 발행하므로 지난달 값은 불변이다. 이미 있으면 <b>외부를 부르지 않는다</b> —
     * 재배포마다 같은 답을 다시 받는 것을 막는 자리다(#226·#231 과 같은 이유).
     */
    boolean existsByBaseDateBetween(LocalDate from, LocalDate to);

    /** 가장 최근에 받은 날 — 어디까지 채웠는지 판단한다. 비어 있으면 아직 아무것도 없다. */
    @Query("select max(daily.baseDate) from RegionVisitorDaily daily")
    LocalDate findMaxBaseDate();
}
