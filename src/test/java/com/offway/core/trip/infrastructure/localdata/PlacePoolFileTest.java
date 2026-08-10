package com.offway.core.trip.infrastructure.localdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 배포에 실리는 장소 풀 파일 자체를 검증한다(#144).
 *
 * <p>통합 테스트는 시나리오 통제를 위해 {@code src/test/resources} 의 소량 풀을 읽는다(테스트 classpath 가 앞선다).
 * 그래서 <b>실제로 배포되는 16만 건 파일이 멀쩡한지는 아무도 안 본다.</b> 여기서 그 파일을 직접 열어 확인한다 —
 * 89곳이 다 채워졌는지, 좌표가 한국 안인지, 스크립트를 다시 돌렸을 때 지역이 비지 않았는지.
 *
 * <p>Spring 을 띄우지 않는다. 파일과 파서만 있으면 되는 검사다.
 */
class PlacePoolFileTest {

    private static final Path POOL_FILE = Path.of("src/main/resources/data/place-pool.csv.gz");

    /** 지역의 정본. INSERT 순서가 곧 region_id 다(생성 스크립트와 같은 전제). */
    private static final Path REGION_SEED =
            Path.of("src/main/resources/db/migration/V20260718200440__create_region_and_seed.sql");

    private static final Pattern SEED_ROW = Pattern.compile("\\('([^']+)','([^']+)'");

    /** 같은 곳을 가리키는 다른 시도 표기 — 생성 스크립트의 SIDO_ALIASES 와 같아야 한다. */
    private static final Map<String, String> SIDO_ALIASES = Map.of("전남광주통합특별시", "전라남도");

    private static final int REGION_COUNT = 89;

    /** 2박3일 빡빡 코스가 요구하는 숙박 수 — 이 이슈의 출발점이 "31곳이 이걸 못 채운다" 였다. */
    private static final int STAYS_FOR_LONGEST_TRIP = 2;

    private static List<LicensedPlace> places;

    @BeforeAll
    static void readPoolFile() throws IOException {
        assertTrue(Files.exists(POOL_FILE), "배포용 장소 풀 파일이 없습니다: " + POOL_FILE);
        try (InputStream in = Files.newInputStream(POOL_FILE)) {
            places = new PlacePoolCsvReader().read(in);
        }
    }

    @Test
    void 배포_파일에_충분한_장소가_들어_있다() {
        assertTrue(places.size() > 100_000, "장소가 너무 적습니다: " + places.size());
    }

    @Test
    void 모든_지역에_숙박이_2곳_이상_있다() {
        Map<Long, Long> stayCounts = places.stream()
                .filter(place -> place.getKind() == PlaceKind.STAY)
                .collect(Collectors.groupingBy(LicensedPlace::getRegionId, Collectors.counting()));

        List<Long> insufficient = LongStream.rangeClosed(1, REGION_COUNT)
                .filter(regionId -> stayCounts.getOrDefault(regionId, 0L) < STAYS_FOR_LONGEST_TRIP)
                .boxed()
                .toList();

        assertEquals(List.of(), insufficient, "숙박이 부족한 지역이 있습니다");
    }

    @Test
    void 모든_지역에_맛집이_있다() {
        Map<Long, Long> foodCounts = places.stream()
                .filter(place -> place.getKind() == PlaceKind.FOOD)
                .collect(Collectors.groupingBy(LicensedPlace::getRegionId, Collectors.counting()));

        List<Long> empty = LongStream.rangeClosed(1, REGION_COUNT)
                .filter(regionId -> foodCounts.getOrDefault(regionId, 0L) == 0)
                .boxed()
                .toList();

        assertEquals(List.of(), empty, "맛집이 없는 지역이 있습니다");
    }

    /** 89곳 밖의 지역 id 가 섞이면 조회되지 않는 채로 용량만 차지한다. */
    @Test
    void 지역_id_가_89곳_안에_있다() {
        List<Long> outOfRange = places.stream()
                .map(LicensedPlace::getRegionId)
                .filter(regionId -> regionId < 1 || regionId > REGION_COUNT)
                .distinct()
                .toList();

        assertEquals(List.of(), outOfRange);
    }

    /**
     * 같은 자리에 같은 상호가 둘이면 코스에 두 번 뜨고, DB 유니크 제약에도 걸려 적재가 통째로 실패한다
     * (원본에는 이마트 푸드코트처럼 한 건물의 여러 업소가 같은 이름으로 올라온다).
     */
    @Test
    void 같은_지역_종류_상호_주소가_중복되지_않는다() {
        Map<String, Long> byNaturalKey = places.stream().collect(Collectors.groupingBy(
                place -> place.getRegionId() + "|" + place.getKind() + "|" + place.getName() + "|" + place.getAddress(),
                Collectors.counting()));

        List<String> duplicates = byNaturalKey.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .limit(5)
                .toList();

        assertEquals(List.of(), duplicates, "중복된 장소가 있습니다");
    }

    /**
     * <b>장소의 주소가 자기 지역을 가리키는가</b>(#222).
     *
     * <p>생성 스크립트가 시군구 이름만 맞으면 시도가 달라도 붙이고 있었다. 89곳에 "동구" 는 부산 하나뿐이라
     * 대구·대전·광주·울산 동구가 전부 부산 동구로 들어왔다 — 15,701건 중 부산이 1,816건(11.6%)뿐이었고,
     * 부산 동구 코스에 수백 km 밖 식당이 뽑혔다.
     *
     * <p>좌표가 멀쩡해서 동선 계산은 되고, 화면에도 그럴듯하게 나온다. <b>지역명을 대조하지 않으면 아무도 모른다.</b>
     */
    @Test
    void 모든_장소의_주소가_자기_지역과_일치한다() throws IOException {
        Map<Long, String[]> seeded = seededRegions();

        List<String> mismatched = places.stream()
                .filter(place -> !addressMatchesRegion(place, seeded.get(place.getRegionId())))
                .map(place -> place.getRegionId() + " " + String.join(" ", seeded.get(place.getRegionId()))
                        + " ← " + place.getAddress())
                .distinct()
                .limit(5)
                .toList();

        assertEquals(List.of(), mismatched, "다른 지역 주소가 섞여 있습니다");
    }

    /**
     * 대형 상업시설 안 매장 제외 키워드 — {@code scripts/build_place_pool.py} 의
     * {@code EXCLUDED_VENUE_KEYWORDS} 와 <b>같은 목록이어야 한다</b>.
     *
     * <p>스크립트는 파이썬이라 상수를 공유할 수 없어 여기 옮겨 적는다. 어긋나면 이 테스트가 통과하는데
     * 배포 파일에는 남는 상황이 생기므로, 한쪽을 고치면 반드시 다른 쪽도 고친다.
     */
    private static final List<String> EXCLUDED_VENUE_KEYWORDS = List.of(
            "백화점", "커넥트현대", "신세계센텀", "롯데몰", "현대시티",
            "아울렛", "아웃렛", "쇼핑몰", "쇼핑센터",
            "이마트", "홈플러스", "롯데마트", "하나로마트", "코스트코");

    /** 위 키워드가 다른 상호의 일부로 걸리는 것 — 스크립트의 {@code VENUE_KEYWORD_EXCEPTIONS} 와 같다. */
    private static final List<String> VENUE_KEYWORD_EXCEPTIONS =
            List.of("제이마트", "아이마트", "타이마트", "씨케이마트");

    /** 편의점 브랜드 — 스크립트의 {@code EXCLUDED_CONVENIENCE_BRANDS} 와 같다. 상호에서만 본다. */
    private static final List<String> CONVENIENCE_BRANDS =
            List.of("씨유", "GS25", "지에스25", "세븐일레븐", "미니스톱");

    /** 스크립트의 {@code CONVENIENCE_BRAND_EXCEPTIONS} 와 같다. */
    private static final List<String> CONVENIENCE_BRAND_EXCEPTIONS = List.of("씨유푸드", "씨유네");

    /** 영문 CU 는 낱말 경계를 본다 — BANH CUON · WACU · CUBE · CUCO COFFEE 가 실제로 걸렸다. */
    private static final Pattern CONVENIENCE_CU = Pattern.compile("(?<![A-Za-z])CU(?![A-Za-z])");

    /**
     * 대형 상업시설 안 매장이 코스 후보로 들어오지 않는가(#222).
     *
     * <p>업태 필터는 업태가 정확히 '백화점' 인 것만 잡는다. 그 안의 식당은 업태가 한식·경양식이라 통과했고,
     * 부산 동구 코스에 커넥트현대 지하 푸드코트가 실제로 들어왔다.
     *
     * <p><b>상호와 주소를 합쳐서 본다.</b> 생성 스크립트가 그렇게 거르기 때문이다 — 한쪽만 보면
     * "남천면가 커넥트현대점"(상호)은 잡고 "범일로 125, 현대백화점 부산점 9층"(주소)은 놓친다.
     */
    @Test
    void 대형_상업시설_안_매장이_들어있지_않다() {
        List<String> inLargeVenue = places.stream()
                .filter(place -> {
                    String haystack = place.getName() + " " + place.getAddress();
                    for (String exception : VENUE_KEYWORD_EXCEPTIONS) {
                        haystack = haystack.replace(exception, "");
                    }
                    String stripped = haystack;
                    return EXCLUDED_VENUE_KEYWORDS.stream().anyMatch(stripped::contains);
                })
                .map(place -> place.getName() + " | " + place.getAddress())
                .limit(5)
                .toList();

        assertEquals(List.of(), inLargeVenue, "대형 상업시설 안 매장이 남아 있습니다");
    }

    /**
     * 편의점이 코스 후보로 들어오지 않는가(#222).
     *
     * <p>업태 필터에 이미 "편의점" 이 있는데도, 휴게음식점·즉석판매로 등록돼 업태가 '커피'·'분식' 인
     * 편의점이 502건 남아 있었다. "편의점·부속매점은 목적지가 아니다" 는 규칙이 새고 있던 것이다.
     *
     * <p><b>상호에서만 본다.</b> 편의점은 작아서 그 안에 다른 가게가 들어갈 수 없고, 주소에 브랜드가
     * 있으면 "그 옆" 이라는 뜻이다. 숙박은 애초에 편의점일 수 없다(씨유민박·씨유비치).
     */
    @Test
    void 편의점이_들어있지_않다() {
        List<String> convenienceStores = places.stream()
                .filter(place -> place.getKind() != PlaceKind.STAY)
                .filter(place -> {
                    String name = place.getName();
                    for (String exception : CONVENIENCE_BRAND_EXCEPTIONS) {
                        name = name.replace(exception, "");
                    }
                    String stripped = name;
                    return CONVENIENCE_BRANDS.stream().anyMatch(stripped::contains)
                            || CONVENIENCE_CU.matcher(stripped).find();
                })
                .map(place -> place.getName() + " | " + place.getAddress())
                .limit(5)
                .toList();

        assertEquals(List.of(), convenienceStores, "편의점이 남아 있습니다");
    }

    /**
     * 시드를 읽어 {@code region_id → (시도, 시군구)} 로 만든다. INSERT 순서가 곧 id 라는 것은
     * 생성 스크립트와 같은 전제다.
     */
    private static Map<Long, String[]> seededRegions() throws IOException {
        String sql = Files.readString(REGION_SEED);
        Matcher matcher = SEED_ROW.matcher(sql);
        Map<Long, String[]> regions = new HashMap<>();
        long id = 0;
        while (matcher.find()) {
            regions.put(++id, new String[] {matcher.group(1), matcher.group(2)});
        }
        assertEquals(REGION_COUNT, regions.size(), "지역 시드를 다 읽지 못했습니다");
        return regions;
    }

    /** 시도 표기 차이만 흡수하고 나머지는 정확히 일치할 것을 요구한다 — 생성 스크립트의 규칙과 같다. */
    private static boolean addressMatchesRegion(LicensedPlace place, String[] region) {
        String[] parts = place.getAddress().split(" ");
        if (parts.length < 2) {
            return false;
        }
        return normalizeSido(parts[0]).equals(normalizeSido(region[0])) && parts[1].equals(region[1]);
    }

    private static String normalizeSido(String sido) {
        return SIDO_ALIASES.getOrDefault(sido, sido);
    }

    /** 관광 API 가 사실상 비어 있던 지역이 실제로 메워졌는지 — 이 작업의 목적 그 자체다. */
    @Test
    void 관광API가_비었던_의성군이_채워졌다() {
        long uiseong = 76L;

        Map<PlaceKind, Long> byKind = places.stream()
                .filter(place -> place.getRegionId() == uiseong)
                .collect(Collectors.groupingBy(LicensedPlace::getKind, Collectors.counting()));

        assertTrue(byKind.getOrDefault(PlaceKind.STAY, 0L) >= 10,
                "의성군 숙박이 부족합니다: " + byKind.get(PlaceKind.STAY));
        assertTrue(byKind.getOrDefault(PlaceKind.FOOD, 0L) >= 10,
                "의성군 맛집이 부족합니다: " + byKind.get(PlaceKind.FOOD));
    }
}
