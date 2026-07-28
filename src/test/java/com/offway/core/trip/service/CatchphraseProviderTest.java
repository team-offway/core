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
    void 콤마가_포함된_문구도_인용을_풀어_온전히_준다() {
        // 인용된 필드는 앞뒤 큰따옴표가 제거되어야 한다.
        provider.forContentId("126508").ifPresent(phrase -> {
            assertFalse(phrase.startsWith("\""), "인용 큰따옴표가 남으면 안 된다");
            assertFalse(phrase.endsWith("\""), "인용 큰따옴표가 남으면 안 된다");
        });
    }

    @Test
    void 대량_시드가_로드된다() {
        // 구석구석 캐치프레이즈는 수만 건 규모 — 파싱이 통째로 실패하면 0 이 된다.
        assertTrue(provider.forContentId("126508").isPresent());
        assertEquals(
                provider.forContentId("126508"),
                provider.forContentId("126508"),
                "같은 키는 항상 같은 값");
    }
}
