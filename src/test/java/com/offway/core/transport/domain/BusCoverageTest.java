package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * TAGO 시내버스 커버리지 판정 단위 테스트. 픽스처는 {@code getCtyCodeList} 실응답 값이다(강원 6곳·경남 일부·광역시).
 *
 * <p>핵심은 <b>동명 시군구를 시도로 갈라내는 것</b>이다. 고성군은 강원(미커버)과 경남(커버) 양쪽에 있어 지명만으로 판정하면
 * 강원 고성군이 커버된다고 잘못 답한다.
 */
class BusCoverageTest {

    /** getCtyCodeList 실응답에서 뽑은 픽스처 — 강원은 6곳뿐이라 정선·평창·고성 등이 빠져 있다. */
    private static final BusCoverage COVERAGE = new BusCoverage(List.of(
            new BusCity(21, "부산광역시"),
            new BusCity(23, "인천광역시"),
            new BusCity(32010, "춘천시"),
            new BusCity(32020, "원주시/횡성군"),
            new BusCity(32050, "태백시"),
            new BusCity(32410, "양양군"),
            new BusCity(38340, "고성군"),
            new BusCity(38390, "거창군")));

    @ParameterizedTest
    @CsvSource({
        "강원특별자치도, 태백시",
        "강원특별자치도, 춘천시",
        "강원특별자치도, 양양군",
        "경상남도, 거창군",
    })
    void 목록에_있는_시군구는_커버된다(String sido, String sigungu) {
        assertTrue(COVERAGE.covers(sido, sigungu));
    }

    @ParameterizedTest
    @CsvSource({
        "강원특별자치도, 정선군",
        "강원특별자치도, 평창군",
        "강원특별자치도, 영월군",
        "강원특별자치도, 삼척시",
        "전라남도, 화순군",
    })
    void 목록에_없는_시군구는_커버되지_않는다(String sido, String sigungu) {
        assertFalse(COVERAGE.covers(sido, sigungu));
    }

    @Test
    void 동명_시군구는_시도로_구분한다() {
        // 고성군은 경남(38340)만 목록에 있다. 지명만 맞추면 강원 고성군이 커버된다고 잘못 답한다.
        assertTrue(COVERAGE.covers("경상남도", "고성군"));
        assertFalse(COVERAGE.covers("강원특별자치도", "고성군"));
    }

    @Test
    void 합본_이름은_각_시군구로_쪼개_인식한다() {
        // TAGO 는 버스권역이 묶인 곳을 "원주시/횡성군" 한 항목으로 준다.
        assertTrue(COVERAGE.covers("강원특별자치도", "원주시"));
        assertTrue(COVERAGE.covers("강원특별자치도", "횡성군"));
    }

    @ParameterizedTest
    @CsvSource({
        "부산광역시, 동구",
        "부산광역시, 서구",
        "부산광역시, 영도구",
        "인천광역시, 강화군",
    })
    void 광역시는_시_단위_하나가_모든_자치구를_커버한다(String sido, String sigungu) {
        // 광역시 코드는 2자리(21·23)로 구 단위가 없다. 구 이름은 목록에 없지만 커버된다.
        assertTrue(COVERAGE.covers(sido, sigungu));
    }

    @Test
    void 서울은_TAGO_대상이_아니라_커버되지_않는다() {
        // 서울은 별도 시스템(TOPIS)이라 getCtyCodeList 에 아예 없다.
        assertFalse(COVERAGE.covers("서울특별시", "종로구"));
    }

    @Test
    void 알_수_없는_시도는_커버되지_않는다() {
        assertFalse(COVERAGE.covers("없는도", "없는군"));
    }
}
