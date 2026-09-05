package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionDailyTourists;
import com.offway.core.trip.domain.RegionVisitorDaily;
import com.offway.core.trip.domain.VisitorType;
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
     * 기간 내 <b>지역·날짜별 관광객 합</b> — 지표 계산이 읽는 모양.
     *
     * <p>유형별 세 줄을 DB 가 하루 한 줄로 접는다. 12만 행을 올려 자바에서 접으면 그 순간 메모리가
     * 튀는데, 운영은 EC2 도커 안 MySQL 하나에 컨테이너 514MB 다.
     *
     * <p><b>관광객 판정을 쿼리에 박지 않는다.</b> "거주자 제외" 는 {@link VisitorType#isTourist()} 가
     * 소유한 규칙이라, 호출자가 그 규칙으로 고른 유형을 넘긴다. 여기에 {@code <> LOCAL} 을 박으면
     * 정본이 둘이 되어 한쪽만 바뀔 수 있다.
     */
    @Query("""
            select new com.offway.core.trip.domain.RegionDailyTourists(
                    daily.signguCode, daily.baseDate, sum(daily.visitorCount))
            from RegionVisitorDaily daily
            where daily.baseDate between :from and :to
              and daily.visitorType in :touristTypes
            group by daily.signguCode, daily.baseDate
            """)
    List<RegionDailyTourists> sumTouristsByRegionAndDate(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("touristTypes") Collection<VisitorType> touristTypes);

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
