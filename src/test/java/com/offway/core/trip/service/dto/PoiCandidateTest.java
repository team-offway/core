package com.offway.core.trip.service.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.PoiContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 후보가 자기 정체를 아는지 — <b>축제 판정</b>(#622).
 *
 * <p>출처가 둘이라 판정이 두 갈래다. TourAPI 축제는 {@code contentTypeId} 가 15 이고, 표준데이터
 * 축제는 그 값이 0 이면서 식별자가 {@code FST-} 다. 한쪽만 보면 나머지가 조용히 빠진다 — 실제로
 * 타입만 보던 동안 코스에 실린 축제의 기간이 안 나갔다.
 *
 * <p>인허가·국가유산도 타입이 0 이라 <b>접두어로 갈라지는지</b>가 이 테스트의 핵심이다.
 */
class PoiCandidateTest {

    private static PoiCandidate candidate(String contentId, int contentTypeId) {
        return PoiCandidate.builder()
                .contentId(contentId)
                .contentTypeId(contentTypeId)
                .title("어딘가")
                .lat(36.3)
                .lng(128.6)
                .build();
    }

    @DisplayName("TourAPI 축제는 콘텐츠 타입으로 가른다")
    @ParameterizedTest
    @ValueSource(strings = {"123456", "999", "2733967"})
    void tourApi축제는_타입15면_축제다(String contentId) {
        assertTrue(candidate(contentId, PoiContentType.FESTIVAL.contentTypeId()).isFestival());
    }

    @DisplayName("표준데이터 축제는 타입이 0 이라 접두어로 가른다")
    @ParameterizedTest
    @ValueSource(strings = {"FST-1", "FST-349", "FST-99999"})
    void 표준데이터축제는_접두어로_축제다(String contentId) {
        assertTrue(candidate(contentId, 0).isFestival(),
                "표준데이터 축제는 contentTypeId 가 0 이라 타입만 보면 놓친다");
    }

    /**
     * 타입 0 을 공유하는 다른 출처가 축제로 새지 않는지.
     *
     * <p>이게 무너지면 인허가 식당이 "축제" 로 한 칸을 예약받는다.
     */
    @DisplayName("타입 0 을 공유하는 다른 출처는 축제가 아니다")
    @ParameterizedTest
    @CsvSource({
        "LIC-1, 인허가",
        "HER-1, 국가유산",
        "CMP-1, 야영장",
        "PET-1, 반려동물동반",
    })
    void 타입0인_다른출처는_축제가_아니다(String contentId, String 출처) {
        assertFalse(candidate(contentId, 0).isFestival(), 출처 + "가 축제로 새고 있다");
    }

    @DisplayName("일반 TourAPI 볼거리·맛집은 축제가 아니다")
    @ParameterizedTest
    @CsvSource({"123456, 12", "123456, 39", "123456, 32", "123456, 28"})
    void 다른_콘텐츠타입은_축제가_아니다(String contentId, int contentTypeId) {
        assertFalse(candidate(contentId, contentTypeId).isFestival());
    }

    @DisplayName("식별자가 없어도 터지지 않는다")
    @ParameterizedTest
    @CsvSource(value = {"null, 12", "null, 0"}, nullValues = "null")
    void 식별자가_없으면_축제가_아니다(String contentId, int contentTypeId) {
        assertFalse(candidate(contentId, contentTypeId).isFestival());
    }

    /** {@code FST} 로 시작하지만 우리 접두어({@code FST-})가 아닌 것은 축제가 아니다. */
    @DisplayName("접두어가 비슷하기만 한 것은 축제가 아니다")
    @ParameterizedTest
    @ValueSource(strings = {"FST", "FSTX-1", "FST_1", "fst-1"})
    void 접두어가_어긋나면_축제가_아니다(String contentId) {
        assertFalse(candidate(contentId, 0).isFestival());
    }
}
