package com.offway.core.region.domain;

import java.util.List;

/**
 * 지역 한 줄 소개(#140) — <b>그 지역에 실제로 있는 것</b>의 이름으로 만든다.
 *
 * <p><b>왜 이렇게 만드나.</b> 화면 명세는 {@code 폐광촌에서 다시 태어난 마을} 같은 감성 문구를 그리는데,
 * 그런 카피는 어디서도 오지 않는다 — 구석구석은 장소(contentId) 단위라 지역 문구가 없고, 관광 API 에
 * 지역 소개 엔드포인트가 없다. 남는 선택은 89곳을 사람이 쓰는 것뿐인데, 그건 실재하는 지역에 대한 주장이라
 * 틀리면 그대로 사용자에게 나간다.
 *
 * <p>그래서 <b>사실만 조합한다</b>. 감성은 없지만 틀리지 않는다.
 */
public record RegionIntro(String text) {

    /** 지역명 접두어 — 국가유산 이름이 {@code 의성 탑리리 오층석탑} 처럼 시군구로 시작한다. */
    private static final String NAME_SEPARATOR = " ";

    /** 카드 한 줄에 들어가는 길이. 넘으면 소개가 아니라 목록이 된다. */
    private static final int MAX_NAME_LENGTH = 14;

    /** 한글 음절 영역 — 조사를 고르려면 마지막 글자의 받침을 봐야 한다. */
    private static final char HANGUL_START = 0xAC00;
    private static final char HANGUL_END = 0xD7A3;
    private static final int JONGSEONG_COUNT = 28;

    /**
     * 대표 볼거리 이름들로 소개를 만든다. 이름이 없으면 소개도 없다(빈 문자열이 아니라 {@code null} 텍스트).
     *
     * @param sigungu 지역명 — 볼거리 이름 앞에 붙은 중복을 떼는 데 쓴다
     */
    public static RegionIntro of(String sigungu, List<String> landmarkNames) {
        List<String> names = landmarkNames.stream()
                .map(name -> stripRegionPrefix(name, sigungu))
                .filter(RegionIntro::isCardFriendly)
                .distinct()
                .limit(2)
                .toList();
        if (names.isEmpty()) {
            return new RegionIntro(null);
        }
        String subject = names.size() >= 2
                ? names.get(0) + particle(names.get(0), "과", "와") + " " + names.get(1)
                : names.get(0);
        String last = names.size() >= 2 ? names.get(1) : names.get(0);
        return new RegionIntro(subject + particle(last, "이", "가") + " 있는 곳");
    }

    /**
     * {@code 의성 탑리리 오층석탑} → {@code 탑리리 오층석탑}.
     *
     * <p>지역명이 이미 카드에 있는데 문구에서 또 반복하면 {@code 의성군 · 경상북도 / 의성 탑리리 오층석탑이
     * 있는 곳} 이 된다. 시군구의 접미사(군·시·구)를 뗀 형태로도 맞춰 본다.
     */
    private static String stripRegionPrefix(String name, String sigungu) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        for (String prefix : List.of(sigungu, sigungu.replaceAll("(시|군|구)$", ""))) {
            if (!prefix.isBlank() && trimmed.startsWith(prefix + NAME_SEPARATOR)) {
                return trimmed.substring(prefix.length() + NAME_SEPARATOR.length()).trim();
            }
        }
        return trimmed;
    }

    /**
     * 카드 한 줄에 넣을 만한 이름인가.
     *
     * <p>길거나 구분자가 섞인 이름({@code 제2로 직봉 - 의성 계란현 봉수 유적})은 뺀다. 소개는 한눈에 읽혀야
     * 하는데 그런 이름이 들어가면 목록처럼 보인다. 뺄 것이 많아 재료가 없으면 소개를 안 만든다 —
     * 어색한 문구보다 없는 편이 낫다.
     */
    private static boolean isCardFriendly(String name) {
        return !name.isBlank() && name.length() <= MAX_NAME_LENGTH && !name.contains(" - ");
    }

    /**
     * 받침에 따라 조사를 고른다 — {@code 오대산사고가} · {@code 극락전이}.
     *
     * <p>{@code 와(과)} 처럼 둘 다 적으면 화면이 어색해진다. 한글은 마지막 글자의 종성으로 결정되므로
     * 계산할 수 있다. 한글이 아닌 글자로 끝나면(숫자·영문) 받침 없는 쪽을 쓴다 — 읽을 때 그쪽이 자연스럽다.
     */
    private static String particle(String word, String withJongseong, String withoutJongseong) {
        char last = word.charAt(word.length() - 1);
        if (last < HANGUL_START || last > HANGUL_END) {
            return withoutJongseong;
        }
        return (last - HANGUL_START) % JONGSEONG_COUNT != 0 ? withJongseong : withoutJongseong;
    }

    /** 소개를 만들 재료가 없었나. */
    public boolean isEmpty() {
        return text == null || text.isBlank();
    }
}
