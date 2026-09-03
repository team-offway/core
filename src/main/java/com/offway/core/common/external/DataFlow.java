package com.offway.core.common.external;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 어느 화면이 어느 외부 API 를 <b>어떻게</b> 쓰는가(#398).
 *
 * <h2>왜 코드가 이걸 들고 있나</h2>
 *
 * <p>지금은 이 사실이 <b>어디에도 없다.</b> 알려면 컨트롤러부터 서비스·provider·port 구현까지 의존
 * 관계를 따라가야 하는데, 실제로 그렇게 훑어야만 "코스 생성이 관광타입 4종을 캐시 없이 부른다" 를
 * 알 수 있었다.
 *
 * <p>문서로 적으면 바로 낡는다. 여기 두면 <b>화면이 그대로 보여주고</b>, 심사 자료(#402 기능설명서
 * 활용 내역)도 이걸 읽어 만든다.
 *
 * <h2>한계 — 이건 선언이지 추적이 아니다</h2>
 *
 * <p>실행 중에 자동으로 채워지는 값이 아니라 <b>사람이 적는 표</b>다. 호출을 새로 붙이면서 여기
 * 갱신을 빠뜨리면 표가 조용히 틀려진다.
 *
 * <p>그럼에도 자동 추적을 쓰지 않은 이유는, {@code CallerContext} 로 모으면 <b>실호출만 잡히고</b>
 * DB 에 캐시된 것을 놓치기 때문이다 — 우리가 가장 많이 쓰는 경로가 정확히 그쪽이다. 반쪽짜리 표는
 * 없느니만 못하다.
 */
public enum DataFlow {

    // ---- 홈 · 지역 -----------------------------------------------------------

    HOME_RANKING(Screen.HOME, ExternalApi.TOUR_DATA_LAB, Mode.LIVE,
            "지역 혼잡도·랭킹. 그달 집계가 이미 있으면 부르지 않는다"),
    HOME_CONTENT(Screen.HOME, ExternalApi.TOUR_API, Mode.CACHED,
            "지역 볼거리 수·카테고리 (6시간)"),
    HOME_PHOTO(Screen.HOME, ExternalApi.TOUR_GALLERY, Mode.STORED,
            "지역 대표 사진 — 배치가 gallery_photo 에 채운다"),

    REGION_LIST_RANKING(Screen.REGION_LIST, ExternalApi.TOUR_DATA_LAB, Mode.LIVE,
            "혼잡도. 홈과 같은 집계를 쓴다"),
    REGION_LIST_CONTENT(Screen.REGION_LIST, ExternalApi.TOUR_API, Mode.CACHED,
            "볼거리 수 (6시간)"),

    REGION_RECOMMEND_RANKING(Screen.REGION_RECOMMEND, ExternalApi.TOUR_DATA_LAB, Mode.LIVE,
            "혼잡도. 도달 가능한 지역만 추린 뒤에도 모집단은 89곳 전체다"),
    REGION_RECOMMEND_CONTENT(Screen.REGION_RECOMMEND, ExternalApi.TOUR_API, Mode.CACHED,
            "볼거리 수 (6시간)"),

    REGION_DETAIL_POI(Screen.REGION_DETAIL, ExternalApi.TOUR_API, Mode.STORED,
            "지역 볼거리 — 배치가 region_poi 에 채운다. 이 화면은 외부를 부르지 않는다"),

    // ---- 코스 ---------------------------------------------------------------

    COURSE_GENERATE_POI(Screen.COURSE_GENERATE, ExternalApi.TOUR_API, Mode.LIVE,
            "슬롯 후보 조회. 관광타입 4종이라 코스 하나에 4콜. 실패하면 인허가 데이터로 폴백한다"),
    COURSE_GENERATE_ROUTE(Screen.COURSE_GENERATE, ExternalApi.TMAP_ROUTE, Mode.LIVE,
            "슬롯 사이 이동 시간"),
    COURSE_GENERATE_WAYPOINT(Screen.COURSE_GENERATE, ExternalApi.TMAP_WAYPOINT, Mode.LIVE,
            "동선 최적화. 하루 한도가 50 뿐이라 가장 먼저 마른다"),
    COURSE_GENERATE_WEATHER(Screen.COURSE_GENERATE, ExternalApi.KMA_WEATHER, Mode.CACHED,
            "여행일 날씨 (3~6시간)"),
    COURSE_GENERATE_TRAIN(Screen.COURSE_GENERATE, ExternalApi.TRAIN_INFO, Mode.CACHED,
            "열차 접근 (6시간)"),
    COURSE_GENERATE_HOLIDAY(Screen.COURSE_GENERATE, ExternalApi.HOLIDAY, Mode.CACHED,
            "공휴일 (24시간)"),
    COURSE_GENERATE_FESTIVAL(Screen.COURSE_GENERATE, ExternalApi.TOUR_API, Mode.STORED,
            "축제 기간 — 배치가 festival_period 에 채운다. 끝난 축제를 여기서 걸러낸다"),
    COURSE_GENERATE_HOURS(Screen.COURSE_GENERATE, ExternalApi.TOUR_API, Mode.STORED,
            "운영시간 — 배치가 poi_intro 에 채운다. 슬롯마다 부르면 코스 하나에 20콜이 된다"),

    COURSE_DETAIL_CONTENT(Screen.COURSE_DETAIL, ExternalApi.TOUR_API, Mode.CACHED,
            "지역 콘텐츠 (6시간)"),
    COURSE_DETAIL_WEATHER(Screen.COURSE_DETAIL, ExternalApi.KMA_WEATHER, Mode.CACHED,
            "여행일 날씨 (3~6시간)"),
    COURSE_DETAIL_TRAIN(Screen.COURSE_DETAIL, ExternalApi.TRAIN_INFO, Mode.CACHED,
            "저장된 출발지로 열차 접근을 다시 계산한다 (6시간)"),

    COURSE_SHARE_NONE(Screen.COURSE_SHARE, null, Mode.STORED,
            "저장된 코스를 그대로 내린다. 외부를 부르지 않는다"),

    // ---- 장소 ---------------------------------------------------------------

    POI_DETAIL(Screen.POI_DETAIL, ExternalApi.TOUR_API, Mode.CACHED,
            "기본정보 + 소개. 새 장소면 2콜 (6시간)"),
    POI_ACCESSIBILITY(Screen.POI_DETAIL, ExternalApi.TOUR_API, Mode.LIVE,
            "무장애 정보. 캐시가 없어 누를 때마다 나간다. 실패하면 폴백도 없다"),

    // ---- 연차 ---------------------------------------------------------------

    LEAVE_AVAILABLE_TIME(Screen.LEAVE_AVAILABLE_TIME, ExternalApi.HOLIDAY, Mode.CACHED,
            "공휴일·샌드위치 연휴 판정 (24시간)");

    /** 사용자가 보는 화면. 경로를 함께 들어 어느 엔드포인트인지 화면이 짚어줄 수 있게 한다. */
    public enum Screen {
        HOME("홈", "GET /api/v1/home"),
        REGION_LIST("지역 목록", "GET /api/v1/regions"),
        REGION_RECOMMEND("지역 추천", "POST /api/v1/regions/recommendations"),
        REGION_DETAIL("지역 상세", "GET /api/v1/regions/{regionId}"),
        COURSE_GENERATE("코스 생성", "POST /api/v1/courses/generate"),
        COURSE_DETAIL("코스 조회", "GET /api/v1/courses/{courseId}"),
        COURSE_SHARE("코스 공유", "POST /api/v1/courses/share"),
        POI_DETAIL("장소 상세", "GET /api/v1/pois/{contentId}"),
        LEAVE_AVAILABLE_TIME("연차 가용시간", "POST /api/v1/leaves/available-time");

        private final String label;
        private final String path;

        Screen(String label, String path) {
            this.label = label;
            this.path = path;
        }

        public String label() {
            return label;
        }

        public String path() {
            return path;
        }
    }

    /**
     * 값을 어디서 가져오나.
     *
     * <p>{@link #LIVE} 와 {@link #CACHED} 를 가르는 이유는 <b>공모전 심사가 그 차이를 본다</b>는 것과,
     * 한도 계산이 달라지기 때문이다. 캐시가 있으면 만료 주기만큼만 나가고, 없으면 요청 수만큼 나간다.
     */
    public enum Mode {
        LIVE("실호출", "요청마다 외부를 부른다. 캐시가 없다"),
        CACHED("캐시", "인메모리 캐시를 지나고, 만료되면 부른다. 재시작하면 비워진다"),
        STORED("DB", "배치가 채운 값을 읽기만 한다. 이 화면은 외부를 부르지 않는다");

        private final String label;
        private final String detail;

        Mode(String label, String detail) {
            this.label = label;
            this.detail = detail;
        }

        public String label() {
            return label;
        }

        public String detail() {
            return detail;
        }
    }

    private final Screen screen;

    /** 외부를 아예 안 쓰는 흐름은 null 이다 — 공유처럼 "부르지 않는다" 도 답이다. */
    private final ExternalApi api;

    private final Mode mode;
    private final String note;

    DataFlow(Screen screen, ExternalApi api, Mode mode, String note) {
        this.screen = screen;
        this.api = api;
        this.mode = mode;
        this.note = note;
    }

    public Screen screen() {
        return screen;
    }

    public ExternalApi api() {
        return api;
    }

    public Mode mode() {
        return mode;
    }

    public String note() {
        return note;
    }

    /** 이 API 를 쓰는 흐름 전부. 실호출을 먼저 보여준다 — 한도를 태우는 쪽이 위에 와야 읽힌다. */
    public static List<DataFlow> using(ExternalApi api) {
        return Arrays.stream(values())
                .filter(flow -> flow.api == api)
                .sorted(Comparator.comparing(DataFlow::mode))
                .toList();
    }

    /** 화면 순서대로 전부. 화면이 표를 그대로 그린다. */
    public static List<DataFlow> all() {
        return Arrays.stream(values())
                .sorted(Comparator.comparing(DataFlow::screen).thenComparing(DataFlow::mode))
                .toList();
    }
}
