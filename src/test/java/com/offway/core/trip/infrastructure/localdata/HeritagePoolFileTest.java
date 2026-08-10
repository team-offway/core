package com.offway.core.trip.infrastructure.localdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.HeritagePlace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 배포에 실리는 국가유산 파일 자체를 검증한다(#160).
 *
 * <p>통합 테스트는 소량 테스트 풀을 읽으므로, 실제로 나가는 3,735건이 온전한지는 여기서만 확인된다.
 * 스크립트를 다시 돌려 파일을 갱신했을 때 커버리지가 조용히 줄어드는 것을 막는 자리다.
 *
 * <p><b>대구 서구(id 5)만 0건인 것이 정상이다.</b> 그 지역의 국가유산이 무형유산 3건뿐이라 방문 대상이 없다.
 * 이 예외를 상수로 박아 두면, 다른 지역이 0 이 되는 순간 테스트가 깨진다.
 */
class HeritagePoolFileTest {

    private static final Path POOL_FILE = Path.of("src/main/resources/data/heritage-pool.csv.gz");

    private static final int REGION_COUNT = 89;

    /** 국가유산이 무형유산뿐이라 방문 대상이 없는 지역 — 대구광역시 서구. */
    private static final long WITHOUT_VISITABLE_HERITAGE = 5L;

    /** 실측 3,735건. 스크립트가 반쪽만 받아도 파일은 만들어지므로 하한을 둔다. */
    private static final int MINIMUM_PLACES = 3_000;

    private static List<HeritagePlace> places;

    @BeforeAll
    static void readPoolFile() throws IOException {
        assertTrue(Files.exists(POOL_FILE), "배포용 국가유산 파일이 없습니다: " + POOL_FILE);
        try (InputStream in = Files.newInputStream(POOL_FILE)) {
            places = new HeritagePoolCsvReader().read(in);
        }
    }

    @Test
    void 배포_파일에_충분한_국가유산이_들어_있다() {
        assertTrue(places.size() >= MINIMUM_PLACES, "국가유산이 너무 적습니다: " + places.size());
    }

    @Test
    void 대구_서구를_뺀_모든_지역에_볼거리가_있다() {
        Map<Long, Long> counts = places.stream()
                .collect(Collectors.groupingBy(HeritagePlace::getRegionId, Collectors.counting()));

        List<Long> empty = LongStream.rangeClosed(1, REGION_COUNT)
                .filter(regionId -> regionId != WITHOUT_VISITABLE_HERITAGE)
                .filter(regionId -> counts.getOrDefault(regionId, 0L) == 0)
                .boxed()
                .toList();

        assertEquals(List.of(), empty, "국가유산이 한 건도 없는 지역이 있습니다");
    }

    @Test
    void 실리는_것은_전부_방문_가능하다() {
        assertTrue(places.stream().allMatch(HeritagePlace::isVisitable),
                "소장 유물·무형유산이 섞여 들어갔습니다");
    }

    @Test
    void 사진은_전부_https_다() {
        // http 로 나가면 앱에서 302 로 튕겨 한 장도 안 뜬다.
        assertTrue(places.stream()
                        .map(HeritagePlace::getImageUrl)
                        .filter(url -> url != null)
                        .allMatch(url -> url.startsWith("https://")),
                "http 이미지가 남아 있습니다");
    }

    @Test
    void 대부분_사진과_설명을_가진다() {
        // 인허가 장소에는 둘 다 없어 카드가 비었다 — 국가유산을 들인 이유가 여기 있다. 실측 98%·99%.
        long withImage = places.stream().filter(place -> place.getImageUrl() != null).count();
        long withDescription = places.stream().filter(place -> place.getDescription() != null).count();

        assertTrue(withImage * 100 / places.size() >= 90, "사진 보유율이 낮습니다: " + withImage);
        assertTrue(withDescription * 100 / places.size() >= 90, "설명 보유율이 낮습니다: " + withDescription);
    }
}
