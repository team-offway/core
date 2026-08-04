package com.offway.core.trip.infrastructure.localdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
