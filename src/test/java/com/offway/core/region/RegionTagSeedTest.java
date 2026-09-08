package com.offway.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.domain.RegionTagType;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.region.repository.RegionTagRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RegionTagSeedTest {

    @Autowired
    private RegionTagRepository regionTagRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void 인구감소지역_태그가_89개_지역에_시딩된다() {
        assertEquals(89, regionTagRepository.countByTag(RegionTagType.POPULATION_DECLINE));
    }

    /**
     * 디지털관광주민증은 <b>89곳이 아니라 52곳</b>이다(#498).
     *
     * <p>참여하지 않는 37곳에서는 발급받아도 쓸 데가 없다. 이 수가 89로 돌아가면 그 37곳에 거짓
     * 뱃지가 나가고, 화면에는 "혜택 있음" 으로만 보여 알아채기 어렵다.
     *
     * <p>명단은 해마다 바뀌므로 <b>이 숫자도 바뀔 수 있다.</b> 다만 바뀔 때는 시드와 함께 의식적으로
     * 고쳐야 한다 — 조용히 어긋나는 것을 막는 것이 이 단언의 목적이다.
     */
    @Test
    void 디지털관광주민증_태그가_참여_52곳에만_시딩된다() {
        assertEquals(52, regionTagRepository.countByTag(RegionTagType.DIGITAL_TOURIST_CARD));
    }

    /**
     * <b>수가 맞아도 지역이 틀릴 수 있다</b> — 그 실수가 나는 자리는 <b>같은 이름의 시군구</b>다.
     *
     * <p>우리 89곳 안에 서구가 둘(부산·대구), 동구가 둘(부산·대구), 고성군이 둘(강원·경남)이다.
     * 시드가 시군구명만 보고 붙으면 <b>수는 그대로인 채</b> 엉뚱한 쪽에 뱃지가 나간다.
     *
     * <p><b>52곳을 전부 나열하지 않는다.</b> 그러면 이 테스트가 시드 SQL 의 복사본이 되고, 복붙으로
     * 옮기는 순간 같은 실수가 양쪽에 생겨 아무것도 못 잡는다. 대신 실제로 갈리는 경계만 본다.
     */
    @Test
    void 디지털관광주민증이_같은_이름의_다른_지역에_붙지_않는다() {
        Set<String> regions = regionsTaggedWith(RegionTagType.DIGITAL_TOURIST_CARD);

        assertTrue(regions.contains("부산광역시 서구"), "부산 서구는 참여 지역이다");
        assertTrue(regions.contains("부산광역시 동구"), "부산 동구는 참여 지역이다");
        assertFalse(regions.contains("대구광역시 서구"), "대구 서구는 참여 지역이 아닌데 같은 이름으로 붙었다");
        assertFalse(regions.contains("대구광역시 남구"), "대구 남구는 참여 지역이 아니다");
    }

    /**
     * 지역 혜택 넷의 대상 수(#498).
     *
     * <p><b>주최가 말하는 수와 다르다.</b> 우리 89곳과 교차한 값이라 — 동해선은 다섯 중 셋,
     * 강원 해양치유는 여섯 중 셋, 반하다 경북은 42개역을 시군으로 옮긴 뒤 아홉이다. 그 교차를
     * 잘못하면 없는 지역에 뱃지가 붙거나 받을 수 있는 지역이 빠진다.
     */
    @Test
    void 지역_혜택_태그가_확인된_대상_수와_맞는다() {
        assertEquals(3, regionTagRepository.countByTag(RegionTagType.DONGHAE_RAIL_PASS), "영덕·울릉·울진");
        assertEquals(3, regionTagRepository.countByTag(RegionTagType.GANGWON_MARINE_HEALING),
                "강원 고성·삼척·양양 — 경남 고성이 섞이면 4가 된다");
        assertEquals(9, regionTagRepository.countByTag(RegionTagType.CHUNGNAM_TRAVEL_FESTA), "충남 전부");
        assertEquals(9, regionTagRepository.countByTag(RegionTagType.GYEONGBUK_RAIL_REFUND), "경북 역이 있는 9곳");
    }

    /**
     * <b>수가 아니라 지역을 본다.</b>
     *
     * <p>위 단언은 행 수만 세므로, 셋이 전부 엉뚱한 지역이어도 통과한다. 특히 강원 해양치유는 주석에
     * "경남 고성이 섞이면 4가 된다" 고 적어 두었는데 — <b>강원 고성이 빠지고 경남 고성이 들어오면
     * 여전히 3이다.</b> 그 경우를 잡는 것이 이 테스트다.
     *
     * <p>대상이 열 곳 아래라 전부 적는다. 52곳짜리와 달리 복사본이 될 만큼 길지 않고, 여기가 바로
     * 동명 시군구가 실제로 걸리는 자리다.
     */
    @Test
    void 지역_혜택_태그가_확인된_그_지역에_붙는다() {
        assertEquals(
                Set.of("경상북도 영덕군", "경상북도 울릉군", "경상북도 울진군"),
                regionsTaggedWith(RegionTagType.DONGHAE_RAIL_PASS));

        assertEquals(
                Set.of("강원특별자치도 고성군", "강원특별자치도 삼척시", "강원특별자치도 양양군"),
                regionsTaggedWith(RegionTagType.GANGWON_MARINE_HEALING),
                "경남 고성이 섞이면 수는 그대로인 채 지역만 틀린다");

        assertEquals(
                Set.of("경상북도 청도군", "경상북도 영덕군", "경상북도 울진군", "경상북도 문경시", "경상북도 봉화군",
                        "경상북도 영주시", "경상북도 안동시", "경상북도 의성군", "경상북도 영천시"),
                regionsTaggedWith(RegionTagType.GYEONGBUK_RAIL_REFUND));
    }

    /**
     * 충남 여행페스타는 <b>시군을 고르지 않고 시도 전체</b>가 대상이다.
     *
     * <p>그래서 목록이 아니라 규칙을 단언한다 — 다른 시도가 하나라도 섞이면 그 지역 사용자가 못 쓰는
     * 혜택을 보게 된다.
     */
    @Test
    void 충남_여행페스타는_충남에만_붙는다() {
        Set<String> regions = regionsTaggedWith(RegionTagType.CHUNGNAM_TRAVEL_FESTA);

        assertFalse(regions.isEmpty());
        assertTrue(regions.stream().allMatch(name -> name.startsWith("충청남도")),
                "충남이 아닌 지역이 섞였다: " + regions);
    }

    @Test
    void 인구감소지역_태그로_89개_지역ID를_역조회한다() {
        assertEquals(89, regionTagRepository.findRegionIdsByTag(RegionTagType.POPULATION_DECLINE).size());
    }

    /**
     * 그 태그가 붙은 지역을 <b>사람이 읽는 이름</b>으로 돌려준다.
     *
     * <p>지역 id 로 비교하면 단언이 틀렸을 때 무엇이 잘못됐는지 읽을 수 없다 — `[11, 24, 37]` 로는
     * 강원 고성인지 경남 고성인지 알 수 없고, 그게 정확히 이 테스트가 잡으려는 실수다.
     */
    private Set<String> regionsTaggedWith(RegionTagType tag) {
        Map<Long, String> names = regionRepository.findAll().stream()
                .collect(Collectors.toMap(Region::getId, region -> region.getSido() + " " + region.getSigungu()));
        return regionTagRepository.findRegionIdsByTag(tag).stream()
                .map(names::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
