package com.offway.core.common.external;

/**
 * 우리가 쓰는 외부 API 와 그 <b>일일 한도</b>(#123). 이 enum 이 한도의 단일 진실 원천이다.
 *
 * <p><b>왜 한 곳에 모으나.</b> 한도가 API 마다 다른데 그 숫자가 어디에도 없었다. 넘겨야만, 그것도 외부 응답이
 * 실패로 바뀌어야만 알았다. 한도를 상수로 들고 있으면 "지금 얼마나 남았나" 를 우리가 계산할 수 있다.
 *
 * <p><b>한도는 계정이 아니라 「활용신청」 단위다.</b> 공공데이터포털은 신청한 서비스마다 따로 하루 한도를 준다.
 * 우리 키는 하나지만 한도는 나뉘어 있어, 관광정보가 말라도 특일정보는 멀쩡하다. 반대로 같은 서비스 안의
 * 오퍼레이션들({@code areaBasedList2}·{@code detailIntro2} 등)은 <b>한 한도를 나눠 쓴다</b>.
 *
 * <p><b>위험한 순서.</b> TMAP 경유지 최적화 50 → 에어코리아 500 → 관광정보 1,000. 앞의 둘은 워밍 한 번에
 * 89곳을 돌면 며칠 못 간다.
 */
public enum ExternalApi {

    /** 한국관광공사 국문 관광정보(15101578). 코스 후보·장소 상세·검색이 이 한도를 함께 쓴다. */
    TOUR_API("국문관광정보", 1_000),

    /** 관광빅데이터(15101972) — 중심 관광지 순위. */
    TOUR_DATA_LAB("관광빅데이터", 1_000),

    /** 관광사진 갤러리. 지역 대표 사진의 출처다. */
    TOUR_GALLERY("관광사진갤러리", 1_000),

    /**
     * 에어코리아 대기질(15073861).
     *
     * <p><b>500 이다.</b> 홈이 지역 카드마다 대기질을 부르는 구조라 워밍 한 번에 89곳을 돌면 다섯 번이면
     * 소진된다. 관광정보 다음이 아니라 그보다 먼저 마른다.
     */
    AIR_KOREA("에어코리아", 500),

    /**
     * TMAP 경유지 최적화 — <b>50</b>. 우리가 가진 것 중 가장 빡빡하다.
     *
     * <p>#110 에서 80% 소진 알림을 실제로 받았다. 코스 하나가 여러 번 부르면 하루에 코스 몇 개를 못 만든다.
     */
    TMAP_WAYPOINT("TMAP 경유지최적화", 50),

    /** TMAP 경로 탐색. 공식 문서에 비공개라 인벤토리 기록값을 쓴다. */
    TMAP_ROUTE("TMAP 경로", 1_000),

    /** 특일정보(15012690) — 공휴일. */
    HOLIDAY("특일정보", 10_000),

    /** TAGO 정류소(15098534). */
    BUS_STOP("TAGO 정류소", 10_000),

    /** TAGO 버스도착(15098530). */
    BUS_ARRIVAL("TAGO 도착정보", 10_000),

    /** TAGO 열차(15098552). */
    TRAIN_INFO("TAGO 열차", 10_000),

    /** 기상청 단기예보(15084084). 중기예보·관광기후지수도 같은 신청 안이다. */
    KMA_WEATHER("기상청 예보", 10_000);

    private final String label;
    private final int dailyLimit;

    ExternalApi(String label, int dailyLimit) {
        this.label = label;
        this.dailyLimit = dailyLimit;
    }

    /** 사람이 읽는 이름 — 로그·응답에 그대로 나간다. */
    public String label() {
        return label;
    }

    /** 하루에 쓸 수 있는 호출 수. KST 자정에 리셋된다. */
    public int dailyLimit() {
        return dailyLimit;
    }

    /** 오늘 이만큼 썼을 때 남은 양. 한도를 넘겼으면 0(음수를 내리지 않는다). */
    public int remainingAfter(long used) {
        return (int) Math.max(0, dailyLimit - used);
    }
}
