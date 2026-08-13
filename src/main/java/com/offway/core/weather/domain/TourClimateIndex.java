package com.offway.core.weather.domain;

import java.time.LocalDate;

/**
 * 관광기후지수 한 시군구·하루치(#130) — "그날 그곳이 관광하기 좋은가" 를 기상청이 계산해 준 값.
 *
 * <p>기온·습도·바람·일조·강수를 합쳐 산출하므로, 우리가 기온과 하늘상태로 다시 조립할 필요가 없다. 인구감소지역 추천에서
 * "언제 가면 좋은가" 에 바로 답할 수 있다.
 *
 * @param date 대상 날짜
 * @param value 지수 원값 — 등급만으로는 같은 등급 안에서 순위를 못 매기므로 함께 담는다(추천 가중치용)
 * @param grade 등급
 */
public record TourClimateIndex(LocalDate date, double value, TourClimateGrade grade) {

    /**
     * 관광기후지수가 답하는 첫 날(오늘 기준) — <b>내일부터</b>다. 오늘을 요청하면 {@code NO_DATA} 가 온다.
     *
     * <p>실호출로 확인했다(2026-08-03).
     */
    public static final int FIRST_DAY = 1;

    /**
     * 마지막 날 — D+9 다.
     *
     * <p><b>상한을 넘겨도 오류가 나지 않는다.</b> D+10 이든 D+12 든 <b>D+9 의 값이 그대로 온다</b>(실측). 요청한
     * 날짜로 라벨을 붙이면 D+9 값을 D+12 라고 내려주게 되므로, 조회 범위를 여기서 막고 응답의 날짜도 따로 검증한다.
     */
    public static final int LAST_DAY = 9;

    /** 이 날짜를 관광기후지수가 답할 수 있는가 — 내일부터 아흐레 뒤까지. */
    public static boolean covers(LocalDate today, LocalDate date) {
        long ahead = java.time.temporal.ChronoUnit.DAYS.between(today, date);
        return ahead >= FIRST_DAY && ahead <= LAST_DAY;
    }

    /** 관광을 권할 만한가 — 추천 가중치·안내 문구의 판단 기준. */
    public boolean recommendable() {
        return grade.recommendable();
    }
}
