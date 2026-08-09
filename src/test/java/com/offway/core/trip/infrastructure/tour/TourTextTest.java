package com.offway.core.trip.infrastructure.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * TourAPI 텍스트 정제(#174) — 원문은 실호출(2026-08-09)에서 딴 것을 쓴다.
 *
 * <p>지어낸 문자열로 테스트하면 실제로 오는 모양을 못 막는다.
 */
class TourTextTest {

    @Test
    void 운영시간의_br_은_줄바꿈으로_바뀐다() {
        // 실측 원문 — 줄 구분이 곧 의미라 지우면 한 줄로 뭉개진다.
        String raw = "[동절기] <br> - 10:00~17:00<br>※ 폐장 30분 전 매표 마감<br>";

        assertEquals("[동절기]\n- 10:00~17:00\n※ 폐장 30분 전 매표 마감", TourText.clean(raw));
    }

    @Test
    void 링크_태그는_텍스트만_남긴다() {
        // homepage 계열이 이 모양이다. 속성에 '>' 가 들어가도 잘리지 않아야 해서 파서를 쓴다.
        String raw = "<a href=\"http://www.jeongseon.go.kr\" target=\"_blank\">정선군 문화관광</a>";

        assertEquals("정선군 문화관광", TourText.clean(raw));
    }

    @Test
    void 속성에_꺾쇠가_들어가도_텍스트가_잘리지_않는다() {
        // 정규식 <[^>]*> 로 지우면 여기서 "b\">운영시간" 이 남는다.
        String raw = "<a title=\"a>b\">운영시간 안내</a>";

        assertEquals("운영시간 안내", TourText.clean(raw));
    }

    @Test
    void HTML_엔티티를_푼다() {
        assertEquals("입장료 & 주차 안내", TourText.clean("입장료 &amp; 주차&nbsp;안내"));
    }

    @Test
    void 연달아_오는_br_은_빈_줄_하나로_줄인다() {
        // 문단 사이 여백으로 <br> 을 여러 개 쓰는 원문이 흔하다. 그대로 두면 화면에 빈 줄이 쌓인다.
        assertEquals("첫 문단\n\n둘째 문단", TourText.clean("첫 문단<br><br><br>둘째 문단"));
    }

    @Test
    void 태그가_없으면_그대로_둔다() {
        assertEquals("연중무휴", TourText.clean("연중무휴"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "<br>", "<p></p>"})
    void 정제하면_비는_값은_null_이다(String raw) {
        // 빈 문자열을 내리면 화면이 "정보 없음" 과 "빈 값" 을 구분하지 못한다.
        assertNull(TourText.clean(raw));
    }

    @Test
    void null_은_null_이다() {
        assertNull(TourText.clean(null));
    }
}
