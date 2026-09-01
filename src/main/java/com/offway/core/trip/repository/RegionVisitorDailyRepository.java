package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionVisitorDaily;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 도메인이 의존하는 port(#394). 구현은 {@link RegionVisitorDailyRepositoryImpl}. */
public interface RegionVisitorDailyRepository {

    /** 그 지역들의 그 기간 방문자. 혼잡도 계산이 읽는 유일한 모양이다. */
    List<RegionVisitorDaily> findBetween(Collection<String> signguCodes, LocalDate from, LocalDate to);

    /**
     * 그 달을 이미 받았는가.
     *
     * <p>원본은 완결된 달만 발행하므로 지난달 값은 <b>불변</b>이다. 참이면 외부를 부를 이유가 없다.
     */
    boolean hasMonth(YearMonth month);

    /** 가장 최근에 받은 날 — 아직 아무것도 없으면 빈 값. */
    Optional<LocalDate> latestDate();

    /**
     * 받은 것을 저장한다 — <b>이미 있는 날은 건너뛴다</b>.
     *
     * @return 실제로 넣은 행 수. 0 이면 전부 이미 있었다는 뜻이다
     */
    int insertIfAbsent(Collection<RegionVisitorDaily> rows);
}
