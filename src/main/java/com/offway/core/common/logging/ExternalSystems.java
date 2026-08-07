package com.offway.core.common.logging;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 외부 호출 URI 를 로그에 실을 짧은 라벨로 옮긴다.
 *
 * <p><b>호스트로는 가를 수 없다.</b> 우리가 부르는 공공 API 열두 개가 전부 {@code apis.data.go.kr} 한 곳에
 * 몰려 있어, 호스트만 쓰면 TourAPI·특일정보·TAGO·코레일·에어코리아가 한 덩어리가 된다. 경로 프리픽스로 가른다.
 *
 * <p>매핑에 없는 주소는 호스트로 떨어진다 — 새 API 를 붙이면서 여기 등록을 잊어도 로그가 비지는 않는다.
 * 다만 같은 호스트끼리 뭉치므로, 새 외부 API 를 추가할 땐 여기도 함께 늘린다.
 * 목록의 정본은 {@code docs/external-api-inventory.md} 다.
 */
public final class ExternalSystems {

    private static final String UNKNOWN = "unknown";

    // data.go.kr 공공 API
    private static final String PATH_TOUR_WITH = "/B551011/KorWithService2";
    private static final String PATH_TOUR = "/B551011/KorService2";
    private static final String PATH_DATALAB = "/B551011/DataLabService";
    private static final String PATH_HOLIDAY = "/B090041/openapi/service/SpcdeInfoService";
    private static final String PATH_AIR = "/B552584/ArpltnInforInqireSvc";
    private static final String PATH_KORAIL = "/B551457/run";
    private static final String PATH_TAGO_ARRIVAL = "/1613000/ArvlInfoInqireService";
    private static final String PATH_TAGO_STATION = "/1613000/BusSttnInfoInqireService";
    private static final String PATH_TRAIN = "/1613000/TrainInfo";
    private static final String PATH_WEATHER_MID = "/1360000/MidFcstInfoService";
    private static final String PATH_WEATHER_SHORT = "/1360000/VilageFcstInfoService_2.0";
    private static final String PATH_WEATHER_TCI = "/1360000/TourStnInfoService1";
    // SK openapi.sk.com
    private static final String PATH_TMAP = "/tmap";

    private static final String LABEL_TOUR_WITH = "tour-with";
    private static final String LABEL_TOUR = "tour";
    private static final String LABEL_DATALAB = "datalab";
    private static final String LABEL_HOLIDAY = "holiday";
    private static final String LABEL_AIR = "air";
    private static final String LABEL_KORAIL = "korail";
    private static final String LABEL_TAGO_ARRIVAL = "tago-arrival";
    private static final String LABEL_TAGO_STATION = "tago-station";
    private static final String LABEL_TRAIN = "train";
    private static final String LABEL_WEATHER_MID = "weather-mid";
    private static final String LABEL_WEATHER_SHORT = "weather-short";
    private static final String LABEL_WEATHER_TCI = "weather-tci";
    private static final String LABEL_TMAP = "tmap";

    /**
     * 경로 프리픽스 → 라벨. 삽입 순서가 곧 조회 우선순위다({@link LinkedHashMap}).
     *
     * <p>{@code /B551011/KorWithService2}(무장애 관광정보)가 {@code /B551011/KorService2}(일반
     * 관광정보)보다 먼저 와야 한다 — 둘 다 같은 접두어를 공유해, 순서가 뒤집히면 무장애관광 경로가
     * {@code startsWith} 매칭에서 짧은 프리픽스인 tour 에 먼저 걸려 tour 로 잘못 라벨링된다.
     */
    private static final Map<String, String> LABELS_BY_PREFIX = new LinkedHashMap<>();

    static {
        LABELS_BY_PREFIX.put(PATH_TOUR_WITH, LABEL_TOUR_WITH);
        LABELS_BY_PREFIX.put(PATH_TOUR, LABEL_TOUR);
        LABELS_BY_PREFIX.put(PATH_DATALAB, LABEL_DATALAB);
        LABELS_BY_PREFIX.put(PATH_HOLIDAY, LABEL_HOLIDAY);
        LABELS_BY_PREFIX.put(PATH_AIR, LABEL_AIR);
        LABELS_BY_PREFIX.put(PATH_KORAIL, LABEL_KORAIL);
        LABELS_BY_PREFIX.put(PATH_TAGO_ARRIVAL, LABEL_TAGO_ARRIVAL);
        LABELS_BY_PREFIX.put(PATH_TAGO_STATION, LABEL_TAGO_STATION);
        LABELS_BY_PREFIX.put(PATH_TRAIN, LABEL_TRAIN);
        LABELS_BY_PREFIX.put(PATH_WEATHER_MID, LABEL_WEATHER_MID);
        LABELS_BY_PREFIX.put(PATH_WEATHER_SHORT, LABEL_WEATHER_SHORT);
        LABELS_BY_PREFIX.put(PATH_WEATHER_TCI, LABEL_WEATHER_TCI);
        LABELS_BY_PREFIX.put(PATH_TMAP, LABEL_TMAP);
    }

    private ExternalSystems() {}

    public static String label(URI uri) {
        if (uri == null) {
            return UNKNOWN;
        }
        String path = uri.getPath();
        if (path != null) {
            for (Map.Entry<String, String> entry : LABELS_BY_PREFIX.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        String host = uri.getHost();
        return host == null ? UNKNOWN : host;
    }
}
