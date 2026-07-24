package com.offway.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌표·TourAPI 코드 backfill(V20260724112101) 정합성 — 89곳 전부 값이 있고 좌표가 한국 범위인지.
 *
 * <p>시드가 UPDATE ... WHERE sido AND sigungu 매칭이라, 이름 불일치로 조용히 빠진 행이 없는지를 여기서 잡는다.
 */
@SpringBootTest
@Transactional
class RegionGeoSeedIntegrationTest {

    /** 한국 위경도 범위 (마라도-울릉도 여유 포함). */
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 38.7;
    private static final double MIN_LNG = 124.5;
    private static final double MAX_LNG = 131.0;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void 인구감소지역_89곳_전부_좌표와_TourAPI_코드가_시딩된다() {
        List<Region> regions = regionRepository.findAll();

        assertEquals(89, regions.size());
        for (Region region : regions) {
            String where = region.getSido() + " " + region.getSigungu();
            assertNotNull(region.getLat(), where + " lat 누락");
            assertNotNull(region.getLng(), where + " lng 누락");
            assertNotNull(region.getAreaCode(), where + " areaCode 누락");
            assertNotNull(region.getSigunguCode(), where + " sigunguCode 누락");
        }
    }

    @Test
    void 시딩된_좌표는_전부_한국_위경도_범위다() {
        for (Region region : regionRepository.findAll()) {
            String where = region.getSido() + " " + region.getSigungu();
            // 범위 단언보다 먼저 — null 이면 언박싱 NPE 로 원인이 가려진다.
            assertNotNull(region.getLat(), where + " lat 누락");
            assertNotNull(region.getLng(), where + " lng 누락");
            assertTrue(region.getLat() >= MIN_LAT && region.getLat() <= MAX_LAT,
                    where + " lat 범위 밖: " + region.getLat());
            assertTrue(region.getLng() >= MIN_LNG && region.getLng() <= MAX_LNG,
                    where + " lng 범위 밖: " + region.getLng());
        }
    }

    @Test
    void 검증된_스팟_지역의_시군구코드가_잠긴값과_일치한다() {
        // sigunguCode 89 전수의 독립 오라클은 없다(기대값 출처가 같은 areaCode2 호출이라 전수 대조는
        // 전사(transcription)일 뿐). 대신 수집 시 사람이 위치까지 확인한 스팟 6곳을 잠가,
        // 이후 마이그레이션이 코드를 잘못 덮는 회귀를 잡는다.
        record Spot(String sigungu, int areaCode, int sigunguCode) {}
        List<Spot> spots = List.of(
                new Spot("완도군", 38, 18),
                new Spot("울릉군", 35, 17),
                new Spot("강화군", 2, 1),
                new Spot("정선군", 32, 11),
                new Spot("괴산군", 33, 1),
                new Spot("합천군", 36, 21));

        List<Region> regions = regionRepository.findAll();
        for (Spot spot : spots) {
            Region region = regions.stream()
                    .filter(r -> r.getSigungu().equals(spot.sigungu()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("스팟 지역 없음: " + spot.sigungu()));
            assertEquals(spot.areaCode(), region.getAreaCode(), spot.sigungu() + " areaCode");
            assertEquals(spot.sigunguCode(), region.getSigunguCode(), spot.sigungu() + " sigunguCode");
        }
    }

    @Test
    void TourAPI_시도코드는_시도명과_일치한다() {
        // 대표 표본 — 코드 체계(KorService2)와 시드 매핑이 어긋나면 여기서 깨진다.
        for (Region region : regionRepository.findAll()) {
            String where = region.getSido() + " " + region.getSigungu();
            int expected = switch (region.getSido()) {
                case "부산광역시" -> 6;
                case "대구광역시" -> 4;
                case "인천광역시" -> 2;
                case "경기도" -> 31;
                case "강원특별자치도" -> 32;
                case "충청북도" -> 33;
                case "충청남도" -> 34;
                case "경상북도" -> 35;
                case "경상남도" -> 36;
                case "전북특별자치도" -> 37;
                case "전라남도" -> 38;
                default -> throw new IllegalStateException("예상 밖 시도: " + where);
            };
            assertEquals(expected, region.getAreaCode(), where + " areaCode 불일치");
        }
    }
}
