package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 지도 검색 링크(#161) — 무엇으로 검색어를 만드는가.
 *
 * <p>여기서 지키는 것은 <b>"엉뚱한 지점이 열리지 않는가"</b> 다. {@code 메가엠지씨커피 공주대점} 같은 이름은
 * 전국에 흩어져 있어, 상호만으로 검색하면 다른 동네 가게가 잡힌다.
 */
class MapSearchLinkTest {

    private static final String PREFIX = "https://map.naver.com/p/search/";

    /** 링크의 검색어 부분을 사람이 읽을 수 있게 되돌린다. */
    private static String queryOf(String url) {
        assertTrue(url.startsWith(PREFIX), "네이버 지도 검색 링크가 아니다: " + url);
        return URLDecoder.decode(url.substring(PREFIX.length()), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @CsvSource({
            "올인모텔,          경상북도 의성군 의성읍 후죽리 1,   의성군 올인모텔",
            "메가엠지씨커피 공주대점, 충청남도 공주시 신관동 산1,      공주시 메가엠지씨커피 공주대점",
            "동아식당,          부산광역시 동구 초량동 1-1,      동구 동아식당",
    })
    void 시군구를_상호_앞에_붙인다(String name, String address, String expected) {
        assertEquals(expected, queryOf(MapSearchLink.of(name, address).orElseThrow()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 상호가_없으면_링크를_만들지_않는다(String blank) {
        // 빈 검색어로 지도를 여는 것은 의미가 없다.
        assertEquals(Optional.empty(), MapSearchLink.of(blank, "경상북도 의성군"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "경상북도"})
    void 소재지가_없거나_시군구까지_없으면_상호만으로_만든다(String poorAddress) {
        // 없는 것보다 낫다. 시군구가 없으면 다른 지점이 잡힐 위험은 남지만 링크 자체는 동작한다.
        assertEquals("올인모텔", queryOf(MapSearchLink.of("올인모텔", poorAddress).orElseThrow()));
    }

    @Test
    void 한글과_공백이_URL_로_인코딩된다() {
        String url = MapSearchLink.of("올인모텔", "경상북도 의성군 의성읍").orElseThrow();

        assertTrue(url.startsWith(PREFIX));
        assertTrue(url.substring(PREFIX.length()).matches("[A-Za-z0-9%+.*_-]+"),
                "인코딩되지 않은 문자가 링크에 남았다: " + url);
    }

    @Test
    void 앞뒤_공백은_검색어에_안_들어간다() {
        assertEquals("의성군 올인모텔", queryOf(MapSearchLink.of("  올인모텔  ", "  경상북도 의성군  ").orElseThrow()));
    }
}
