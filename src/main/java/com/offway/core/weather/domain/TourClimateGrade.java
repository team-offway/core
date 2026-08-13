package com.offway.core.weather.domain;

import java.util.Arrays;

/**
 * 관광기후지수 등급(#130) — 기상청이 "그 시군구가 관광하기 좋은 날씨인가" 를 계산해 매긴 등급.
 *
 * <p>기온·습도·바람·일조·강수를 합쳐 기상청이 이미 산출한 값이라, 우리가 기온과 하늘상태로 다시 조립할 필요가 없다.
 *
 * <p>응답이 <b>한글 문구</b>로 오므로 문구를 그대로 상수에 싣는다. 모르는 문구는 {@link #UNKNOWN} 으로 떨어뜨리되
 * 조용히 넘기지 않는다 — 등급 체계가 바뀌면 드러나야 한다.
 */
public enum TourClimateGrade {

    VERY_GOOD("매우좋음"),
    GOOD("좋음"),
    NORMAL("보통"),
    BAD("나쁨"),
    VERY_BAD("매우나쁨"),
    UNKNOWN("정보 없음");

    private final String label;

    TourClimateGrade(String label) {
        this.label = label;
    }

    /** 화면 노출 한글 라벨. 기상청 문구와 같다(UNKNOWN 제외). */
    public String label() {
        return label;
    }

    /** 관광을 권할 만한 등급인가 — 추천 가중치·안내 문구의 판단 기준. */
    public boolean recommendable() {
        return this == VERY_GOOD || this == GOOD;
    }

    /** 기상청 등급 문구 → 상수. 모르는 문구·null 은 UNKNOWN. */
    public static TourClimateGrade fromLabel(String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = text.trim();
        return Arrays.stream(values())
                .filter(grade -> grade != UNKNOWN && grade.label.equals(trimmed))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
