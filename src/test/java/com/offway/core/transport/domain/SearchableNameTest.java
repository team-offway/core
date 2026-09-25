package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 검색용 이름 정규화 — 구두점이 사용자가 치는 말을 막지 않게 한다(#590). */
class SearchableNameTest {

    @ParameterizedTest
    @CsvSource({
        // 가운뎃점 — 광주종합터미널의 원본 이름이 이렇다
        "광주(유·스퀘어),광주유스퀘어",
        // 괄호
        "센트럴시티(서울),센트럴시티서울",
        "청주(고속),청주고속",
        "부산서부(사상),부산서부사상",
        // 공백
        "서울 고속버스터미널,서울고속버스터미널",
        // 하이픈·쉼표
        "수락산역(직통),수락산역직통",
        // 목록에 없던 구두점도 지워진다 — 열거하면 빠뜨린 글자가 검색을 막는다.
        // 작은따옴표는 @CsvSource 의 인용문자라 케이스로 쓸 수 없다(파싱이 깨진다).
        "서울역!,서울역",
        "서울역?,서울역",
        "서울#역,서울역",
        "서울*역,서울역",
        "서울+역,서울역",
        // 바꿀 것이 없으면 그대로
        "동서울,동서울",
    })
    void 구두점과_공백을_걷어낸다(String raw, String expected) {
        assertEquals(expected, SearchableName.of(raw).value());
    }

    @Test
    void 가운뎃점을_지운_덕에_유스퀘어로_찾힌다() {
        // 이 한 건이 정규화를 만든 이유다 — 별칭 목록으로 메우려면 구두점 조합마다 항목이 늘어난다.
        SearchableName name = SearchableName.of("광주(유·스퀘어)");

        assertTrue(name.contains(SearchableName.of("유스퀘어")));
        assertTrue(name.contains(SearchableName.of("광주")));
    }

    @Test
    void 품지_않으면_거짓이다() {
        assertFalse(SearchableName.of("동서울").contains(SearchableName.of("부산")));
    }

    @Test
    void 구두점만_있는_검색어는_빈_이름이_된다() {
        // 서비스가 이것을 짧은 검색어와 같게 다뤄 빈 목록으로 답한다.
        assertTrue(SearchableName.of("()·  ").isBlank());
    }
}
