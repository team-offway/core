package com.offway.core.trip.infrastructure.tour;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

/**
 * TourAPI 텍스트를 화면에 그대로 쓸 수 있게 정제한다(#174).
 *
 * <p><b>왜 백엔드가 하나.</b> 예전에는 FE 가 앱에서 태그를 지웠다. 그건 "클라이언트가 떠안을 일을 백엔드가
 * 미리 끝내둔다" 는 성능 규약과 어긋나고, 클라이언트가 둘 이상이 되면 각자 같은 정리를 다시 짜야 한다.
 *
 * <p><b>{@code <br>} 은 지우지 않고 줄바꿈으로 바꾼다.</b> 실측(2026-08-09)에서 운영시간이 이렇게 온다 —
 * 줄 구분이 곧 의미다.
 *
 * <pre>
 *   [동절기] &lt;br&gt; - 10:00~17:00&lt;br&gt;※ 폐장 30분 전 매표 마감&lt;br&gt;
 * </pre>
 *
 * <p><b>정규식으로 하지 않는다.</b> {@code <a href="a>b">} 처럼 속성에 {@code >} 가 들어가면 텍스트가
 * 잘린다. 깨진 문구가 사용자에게 나가면 되돌리기 어려워 파서를 쓴다.
 */
final class TourText {

    /** 그 자리가 곧 줄바꿈인 태그. */
    private static final String INLINE_BREAK_TAG = "br";

    /** 블록이 끝나면 줄이 바뀐다 — 뒤에 개행을 붙인다. */
    private static final String BLOCK_TAGS = "p, div, li, tr";

    private TourText() {
    }

    /**
     * 태그를 걷어내고 HTML 엔티티를 푼다. 줄 구분은 살린다.
     *
     * @return 정제된 문자열. 입력이 null 이거나 정제 후 비면 <b>null</b> — 빈 문자열을 내려 화면이
     *     "아무것도 없음" 과 "빈 값" 을 구분하지 못하는 일을 만들지 않는다
     */
    static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Document document = Jsoup.parse(raw, "", Parser.htmlParser());
        document.outputSettings().prettyPrint(false);
        // 개행을 텍스트 노드로 심는다. 자리표시자 문자열을 쓰면 원문에 같은 글자가 있을 때 오작동하고,
        // text() 는 공백을 정규화하면서 그 표시자까지 뭉갠다 — wholeText() 로 원문 공백을 지킨다.
        document.select(INLINE_BREAK_TAG).forEach(element -> element.replaceWith(new TextNode("\n")));
        document.select(BLOCK_TAGS).forEach(element -> element.appendChild(new TextNode("\n")));

        return normalize(document.wholeText());
    }

    /**
     * 공백으로 볼 문자 — 자바 정규식의 {@code \s} 는 <b>non-breaking space(U+00A0)를 포함하지 않는다.</b>
     *
     * <p>{@code &nbsp;} 가 흔히 오는데 그걸 그대로 두면 눈으로는 공백인데 문자열 비교·정리에서는 다른 값이라
     * 조용히 어긋난다. 폭 0 공백(U+200B)도 같은 이유로 함께 본다.
     */
    private static final String WHITESPACE = "[\\s\\u00A0\\u200B]+";

    /**
     * 줄 안의 공백은 하나로, 줄 끝 공백은 제거, 빈 줄이 잇따르면 하나로 줄인다.
     *
     * <p>원문에 {@code <br>} 이 연달아 오는 경우가 흔한데(문단 사이 여백) 그대로 두면 화면에 빈 줄이 여러
     * 개 생긴다.
     */
    private static String normalize(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean previousBlank = true; // 앞쪽 빈 줄부터 잘라낸다
        for (String line : lines) {
            String trimmed = line.replaceAll(WHITESPACE, " ").strip();
            if (trimmed.isEmpty()) {
                if (!previousBlank) {
                    result.append('\n');
                }
                previousBlank = true;
                continue;
            }
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(trimmed);
            previousBlank = false;
        }
        String cleaned = result.toString().strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
