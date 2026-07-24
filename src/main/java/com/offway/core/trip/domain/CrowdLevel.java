package com.offway.core.trip.domain;

/**
 * 한산도 뱃지 — 지역이 평일에 얼마나 붐비는지(집중률). 랭킹 점수엔 관여하지 않고 카드에 표시만 한다(랭킹 방향 A).
 *
 * <p>v1 은 실측 일평균 방문자수를 절대 임계로 버킷팅한다. 진짜 집중률(생활인구 대비 등)은 데이터 확보 후 후속에서 정교화한다
 * (course-logic "임계값 확정 시 갱신").
 */
public enum CrowdLevel {

    /** 평일 여유. */
    LOW,

    /** 보통. */
    MID,

    /** 붐빔. */
    HIGH;

    /** MID 하한(일평균 방문자수) — 미만은 LOW. v1 잠정치. */
    private static final double MID_MIN_MEAN_DAILY = 3000;

    /** HIGH 하한(일평균 방문자수) — 이상은 HIGH. v1 잠정치. */
    private static final double HIGH_MIN_MEAN_DAILY = 10000;

    /** 실측 일평균 방문자수로 혼잡도 뱃지를 가른다. */
    public static CrowdLevel of(double meanDailyTourists) {
        if (meanDailyTourists >= HIGH_MIN_MEAN_DAILY) {
            return HIGH;
        }
        if (meanDailyTourists >= MID_MIN_MEAN_DAILY) {
            return MID;
        }
        return LOW;
    }
}
