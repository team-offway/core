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
 * <p><b>위험한 순서.</b> TMAP 경유지 최적화 50 → 관광정보 1,000. 앞의 것은 코스 하나가 여러 번 부르면
 * 하루에 코스 몇 개를 못 만든다.
 *
 * <p>에어코리아(500)는 여기 있었는데 그 API 자체를 걷어냈다(#247) — 코스 판단에 쓰이지 않는 값이 두 번째로
 * 빡빡한 한도를 혼자 먹고 있었다. 이 표가 그 판단의 근거가 됐다.
 */
public enum ExternalApi {

    /** 한국관광공사 국문 관광정보(15101578). 코스 후보·장소 상세·검색이 이 한도를 함께 쓴다. */
    TOUR_API("국문관광정보", 1_000),

    /** 관광빅데이터(15101972) — 중심 관광지 순위. */
    TOUR_DATA_LAB("관광빅데이터", 1_000),

    /** 관광사진 갤러리. 지역 대표 사진의 출처다. */
    TOUR_GALLERY("관광사진갤러리", 1_000),

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

    /** TAGO 고속버스(15098522) — 터미널 목록·구간 소요시간(#107). */
    EXPRESS_BUS_INFO("TAGO 고속버스", 10_000),

    /** TAGO 시외버스(15098541) — 터미널 목록·구간 소요시간(#97). */
    INTERCITY_BUS_INFO("TAGO 시외버스", 10_000),

    /** TAGO 국내선박운항정보(15098523) — 항구 목록·구간 소요시간(#97). 섬 지역의 유일한 수단이다. */
    SHIP_INFO("TAGO 여객선", 10_000),

    /** 기상청 단기예보(15084084). 중기예보·관광기후지수도 같은 신청 안이다. */
    KMA_WEATHER("기상청 예보", 10_000);

    /**
     * 알림 단계 수 — 10% 씩 열 단계(#257).
     *
     * <p>퍼센트로 나누므로 한도가 작을수록 촘촘해진다. TMAP 50 은 5건마다, TAGO 10,000 은 1,000건마다다.
     * <b>의도한 결과다</b> — 빡빡한 쪽을 자주 보게 된다.
     */
    private static final int NOTIFY_STEPS = 10;

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

    /**
     * 이만큼 썼을 때의 알림 단계 — {@code 0}(10% 미만)부터 {@code 10}(한도 도달)까지.
     *
     * <p><b>상한이 있다.</b> 한도를 넘겨도 10 에서 멈춘다. 초과분마다 단계가 오르면 안 고치는 동안 계속
     * 울리고, 며칠이면 아무도 안 보게 된다 — 그러면 알림이 없는 것과 같다.
     */
    public int usageStep(long used) {
        if (used <= 0) {
            return 0;
        }
        long step = used * NOTIFY_STEPS / dailyLimit;
        return (int) Math.min(NOTIFY_STEPS, step);
    }

    /** 단계를 퍼센트로. 화면·알림 문구에 그대로 나간다. */
    public static int percentOf(int step) {
        return step * (100 / NOTIFY_STEPS);
    }
}
