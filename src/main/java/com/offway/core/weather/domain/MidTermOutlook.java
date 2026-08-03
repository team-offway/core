package com.offway.core.weather.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 중기 육상예보 한 구역의 전망(#129) — 발표일 기준 <b>D+4 부터 D+10 까지</b> 날짜별 하늘상태·강수확률.
 *
 * <p><b>기온은 담지 않는다.</b> 기상청은 중기 기온을 육상예보와 다른 <b>시군 단위 지점 코드</b>로 제공하는데, 우리 89개
 * 지역의 지점 코드를 아직 갖고 있지 않다. 광역 구역 코드를 기온 조회에 넣으면 오류가 아니라 <b>0℃가 조용히</b> 오므로,
 * 근사값을 지어내는 대신 비워 둔다({@link DailyWeather} 의 기온은 nullable).
 *
 * @param baseDate 발표일 — 날짜별 값이 이 날로부터 며칠 뒤인지로 정해진다
 * @param byDate 날짜별 전망
 */
public record MidTermOutlook(LocalDate baseDate, Map<LocalDate, DayOutlook> byDate) {

    /** 중기예보가 답하는 첫 날(발표일 기준). 이보다 가까우면 단기예보가 답한다. */
    public static final int FIRST_DAY = 4;

    /** 중기예보가 답하는 마지막 날. 이보다 멀면 어떤 예보도 없다. */
    public static final int LAST_DAY = 10;

    public MidTermOutlook {
        byDate = Map.copyOf(byDate);
    }

    /** 이 날짜의 전망. 범위 밖이면 빈 Optional. */
    public Optional<DailyWeather> on(LocalDate date) {
        return Optional.ofNullable(byDate.get(date))
                .map(outlook -> new DailyWeather(date, null, null, outlook.sky(), outlook.rainProbability()));
    }

    /** 이 날짜를 중기예보가 답할 수 있는가 — 오늘로부터 4일에서 10일 사이. */
    public static boolean covers(LocalDate today, LocalDate date) {
        long ahead = java.time.temporal.ChronoUnit.DAYS.between(today, date);
        return ahead >= FIRST_DAY && ahead <= LAST_DAY;
    }

    /**
     * 하루치 전망.
     *
     * <p>중기예보는 D+4 에서 D+7 까지는 오전·오후를 나눠 주고 D+8 부터는 하루 하나만 준다. 하루 요약으로 합칠 때
     * <b>강수확률은 큰 쪽</b>을 쓴다 — "오후에 비" 를 "비 안 옴" 으로 뭉개면 여행 계획이 틀어진다.
     *
     * @param sky 하늘상태
     * @param rainProbability 강수확률 최대(%)
     */
    public record DayOutlook(SkyState sky, Integer rainProbability) {
    }
}
