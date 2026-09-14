package com.offway.core.trip.repository;

import com.offway.core.trip.domain.AttractionCrowdForecast;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 집중률 예측 저장소 port(#565). 도메인·서비스는 이 인터페이스에만 의존한다. */
public interface AttractionCrowdForecastRepository {

    /**
     * 그 지역의 <b>그 날짜들</b> 예보 — 코스 한 건이 쓰는 만큼만 읽는다.
     *
     * <p>지역 하나가 관광지 수 × 30일이라 전량을 읽지 않는다. 코스는 길어야 사흘이므로 날짜를 좁히면
     * 읽는 행이 관광지 수 × 3 으로 준다.
     */
    List<AttractionCrowdForecast> findByRegionAndDates(long regionId, Collection<LocalDate> dates);

    long count();

    /**
     * 한 지역의 예보를 <b>통째로 갈아 끼운다</b> — 지우고 넣는 것이 한 트랜잭션이다.
     *
     * <p><b>왜 전역 정리가 아니라 지역 단위인가.</b> 이 배치는 89곳을 하나씩 도는데 지역마다 실패가
     * 갈린다. "이번 회차에 안 온 것" 을 전역으로 지우면 <b>호출이 실패한 지역의 예보까지 지운다</b> —
     * 받지 못한 것과 사라진 것이 구분되지 않는다.
     *
     * <p>지역 단위로 갈아 끼우면 그 판정이 필요 없다. 받은 지역만 새 값이 되고, 못 받은 지역은 옛 값을
     * 그대로 들고 있는다. 예보는 날짜가 붙어 있어 옛 값도 그 날짜까지는 유효하다.
     *
     * <p><b>빈 목록으로 부르지 않는다.</b> 그러면 그 지역이 통째로 비는데, 응답이 비어 온 것과 그
     * 지역에 관광지가 없어진 것은 구분되지 않는다(성능 규약 "빈 응답을 성공으로 캐시하지 않는다").
     * 호출자가 먼저 거른다.
     *
     * @return 새로 넣은 행 수
     */
    int replaceRegion(long regionId, Collection<AttractionCrowdForecast> forecasts);

    /**
     * 지나간 날짜의 예보를 지운다 — 지역과 무관하게 <b>언제나 안전한</b> 정리다.
     *
     * <p>예보 창이 향후 30일이라 지난 날짜는 다음 회차에 안 온다. 갈아 끼우기만 하면 호출이 계속
     * 실패하는 지역의 옛 행이 영영 남으므로, 날짜로 한 번 쓸어 준다.
     *
     * <p>이건 "이번에 안 왔다" 가 아니라 "이미 지난 날이다" 라는 판정이라, 어느 지역이 실패했든
     * 결과가 달라지지 않는다.
     *
     * @param date 이 날짜보다 이전(미포함)의 행을 지운다
     * @return 지운 행 수
     */
    int deleteBefore(LocalDate date);
}
