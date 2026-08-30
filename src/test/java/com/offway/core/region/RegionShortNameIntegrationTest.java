package com.offway.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 알림 문구에 넣을 짧은 지명(#356) — <b>실제 89곳으로</b> 확인한다.
 *
 * <p>{@code Region} 은 마이그레이션이 채우는 표라 생성자가 없다. 지어낸 이름으로 도는 것보다 <b>진짜 목록</b>
 * 으로 도는 편이 강하다 — 자치구 다섯 곳이 실제로 여기 있고, 앞으로 지역이 바뀌어도 이 테스트가 같이 본다.
 */
@SpringBootTest
class RegionShortNameIntegrationTest {

    /** 자치구는 접미사를 떼지 않는다. 실제 89곳에 있는 다섯이다. */
    private static final List<String> DISTRICTS = List.of("남구", "동구", "서구", "영도구");

    @Autowired
    private RegionRepository regionRepository;

    private Map<String, String> shortNames() {
        return regionRepository.findAll().stream()
                .collect(Collectors.toMap(Region::getSigungu, Region::shortName, (a, b) -> a));
    }

    @Test
    void 군_시는_떼서_문장에_넣을_수_있게_한다() {
        Map<String, String> shortNames = shortNames();

        assertEquals("정선", shortNames.get("정선군"));
        assertEquals("완도", shortNames.get("완도군"));
        assertEquals("공주", shortNames.get("공주시"));
        assertEquals("태백", shortNames.get("태백시"));
    }

    /**
     * <b>여기가 이 메서드의 존재 이유다.</b> 한 글자를 무턱대고 떼면 {@code 동구} 가 {@code 동}, {@code 남구}
     * 가 {@code 남} 이 되어 지명이 아니게 된다. 앱이 접미사를 떼면 이 다섯 곳에서 같은 함정을 밟는다.
     */
    @Test
    void 자치구는_그대로_둔다() {
        Map<String, String> shortNames = shortNames();

        for (String district : DISTRICTS) {
            assertEquals(district, shortNames.get(district), district + " 의 접미사가 떨어졌습니다");
        }
    }

    /** 89곳 전수 — 어느 이름도 빈 문자열이나 한 글자로 뭉개지지 않는다. */
    @Test
    void 어느_지역도_한_글자로_뭉개지지_않는다() {
        for (Region region : regionRepository.findAll()) {
            String shortName = region.shortName();
            assertFalse(shortName.isBlank(), region.getSigungu() + " 의 짧은 이름이 비었습니다");
            assertTrue(
                    shortName.length() >= 2,
                    region.getSigungu() + " → " + shortName + " (한 글자는 지명으로 안 읽힌다)");
        }
    }

    /** 시도는 붙이지 않는다 — 전남광주통합특별시가 붙으면 배너 한 줄에 안 들어간다(#348). */
    @Test
    void 시도를_붙이지_않는다() {
        for (Region region : regionRepository.findAll()) {
            assertFalse(
                    region.shortName().contains(region.getSido()),
                    region.getSigungu() + " 의 짧은 이름에 시도가 붙어 있습니다: " + region.shortName());
        }
    }
}
