package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.TransitLegResult;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 버스·여객선 <b>구간 소요시간</b> 조회 port(#107 · #97). 출발·도착 코드로 그 구간이 몇 분 걸리는지 잰다.
 *
 * <p><b>날짜는 "언제 재는가" 일 뿐 결과의 일부가 아니다.</b> 조회창이 오늘~+2일(여객선 +7일)뿐이라 미래 날짜는
 * 애초에 못 묻는데, 소요시간에는 편차가 없어(실측 2026-08-31) 오늘 재서 그대로 쓸 수 있다. 그래서 반환값은
 * 시각이 아니라 {@link MeasuredLeg}(소요시간·요금·등급)다.
 *
 * <p>열차는 여기 없다 — {@link TrainInfoClient} 가 실제 시각까지 답한다.
 */
public interface TransitLegClient {

    /**
     * 구간 하나의 소요시간. 편이 여럿이면 가장 짧은 것을 고른다.
     *
     * @param mode {@link TransitMode#TRAIN} 이 아닌 수단
     * @param depCode 출발 터미널·항구 코드
     * @param arrCode 도착 터미널·항구 코드
     * @param date 조회일자 — 조회창 안이어야 한다(보통 오늘)
     * @return 잰 구간 / 그 구간 운행 없음 / 조회 불가 — 셋을 구분한다. 결과를 DB 에 영구 기록하므로
     *     "없다" 와 "못 물었다" 를 뭉개면 멀쩡한 구간이 미운행으로 굳는다({@link TransitLegResult})
     */
    TransitLegResult measure(TransitMode mode, String depCode, String arrCode, LocalDate date);

    /**
     * 그 날짜 그 구간의 <b>운행 편 전부</b> — 몇 시 차가 있는지(#414).
     *
     * <p>{@link #measure} 와 같은 응답을 읽지만 버리는 것이 다르다. 그쪽은 소요시간만 남기고 시각을 버리는데,
     * 시간표는 시각이 전부다.
     *
     * <p><b>조회창 안의 날짜에만 부른다.</b> 고속·시외버스는 오늘~+2일, 여객선은 오늘~+7일만 답한다
     * (실측 2026-08-31). 그 밖의 날짜를 물으면 빈 결과가 오고 한도만 깎인다 — 판단은 부르는 쪽이 한다
     * ({@link TransitMode#lookaheadDays}).
     *
     * @return 운행 편. <b>빈 목록</b>이면 그 날짜에 운행이 없거나 조회에 실패한 것이다 — 시간표는 없어도
     *     화면이 소요시간으로 그려지므로 둘을 가르지 않는다({@link #measure} 와 다른 점이다)
     */
    List<Departure> departures(TransitMode mode, String depCode, String arrCode, LocalDate date);
}
