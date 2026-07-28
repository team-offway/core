package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 캐치프레이즈 CSV 시드 로딩·조회 단위 테스트. Spring·DB 없이 클래스패스 CSV 를 그대로 읽어
 * 존재/부재/콤마 포함 문구(CSV 인용 해제)를 검증한다.
 */
class CatchphraseProviderTest {

    private CatchphraseProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CatchphraseProvider();
        provider.load();
    }

    @Test
    void 시드에_있는_contentId면_캐치프레이즈를_준다() {
        assertTrue(provider.forContentId("126508").isPresent());
    }

    @Test
    void 시드에_없는_contentId면_빈_Optional이다() {
        assertTrue(provider.forContentId("이런ID는없다").isEmpty());
        assertTrue(provider.forContentId("0").isEmpty());
    }

    @Test
    void 콤마가_포함된_인용_문구도_콤마를_보존해_온전히_준다() {
        // 134640 은 인용부호로 감싸이고 내부에 콤마가 있는 실제 시드 행("...인삼, 대추...").
        // 첫 콤마로 contentId 를 자르되 인용을 풀어, 문구 안의 콤마는 그대로 보존돼야 한다.
        String phrase = provider.forContentId("134640").orElseThrow();

        assertEquals("신촌약수물에 인삼, 대추 등의 약재를 넣은 닭죽 전문점", phrase);
        assertFalse(phrase.startsWith("\""), "인용 큰따옴표가 남으면 안 된다");
    }
}
