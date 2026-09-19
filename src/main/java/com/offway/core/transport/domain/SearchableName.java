package com.offway.core.transport.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 검색에 쓰기 위해 잡음을 걷어낸 이름(#590).
 *
 * <p><b>왜 필요한가.</b> 원본 이름에 구두점이 섞여 있어 글자 그대로 비교하면 사용자가 치는 말이 안
 * 걸린다. 광주종합터미널의 원본 이름은 {@code 광주(유·스퀘어)} 라서, {@code 유스퀘어} 를 쳐도
 * 가운뎃점 때문에 걸리지 않는다. {@code 청주(고속)}·{@code 부산서부(사상)}·{@code 울산(태화)} 도 같다.
 *
 * <p>이걸 별칭 목록으로 메우려 하면 구두점 조합마다 항목이 하나씩 늘어난다 — 규칙으로 걷어내면 그
 * 목록이 필요 없다. 별칭은 <b>진짜로 다른 이름</b>에만 남긴다({@link OriginHubAlias}).
 *
 * <p>대소문자는 다루지 않는다 — 대상이 한국어 지명이고, 라틴 문자가 섞이는 허브 이름이 없다.
 */
public record SearchableName(String value) {

    /**
     * 구두점·공백·기호 — 사용자가 치지 않거나 다르게 치는 것들.
     *
     * <p><b>범주로 잡는다.</b> 목록으로 열거하면 빠뜨린 글자가 검색을 막는다 — {@code 서울역!} 의
     * {@code !} 가 남아 {@code 서울역} 과 안 맞는 식이다. {@code \p{P}}(구두점)와 {@code \p{S}}(기호)로
     * 넓히면 새 조합도 같은 규칙을 탄다.
     *
     * <p>{@code ㆍ}(U+318D)는 <b>범주에 안 들어간다</b> — 유니코드가 이것을 한글 문자(Lo)로 분류해서다.
     * 그런데 {@code 광주(유·스퀘어)} 같은 이름에 실제로 쓰이므로 따로 적어 둔다. 범주만 믿고 지우면
     * 이 한 글자가 조용히 남는다.
     */
    private static final Pattern NOISE = Pattern.compile("[\\s\\p{P}\\p{S}ㆍ]");

    public SearchableName {
        Objects.requireNonNull(value, "검색 이름은 필수입니다");
    }

    public static SearchableName of(String raw) {
        Objects.requireNonNull(raw, "원본 이름은 필수입니다");
        return new SearchableName(NOISE.matcher(raw).replaceAll(""));
    }

    public boolean isBlank() {
        return value.isBlank();
    }

    /** 이 이름이 상대를 품는가 — 검색어 포함 판정. */
    public boolean contains(SearchableName other) {
        return value.contains(other.value);
    }
}
