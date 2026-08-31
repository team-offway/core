package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.TransitLegResult;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDate;

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
}
