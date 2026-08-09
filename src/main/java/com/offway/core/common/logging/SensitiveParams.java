package com.offway.core.common.logging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 로그에 남기기 전에 쿼리스트링의 민감한 값을 가린다.
 *
 * <p><b>화이트리스트가 아니라 거부 목록으로 간다.</b> 화이트리스트면 파라미터가 늘 때마다 등록을 빠뜨려
 * 조용히 안 찍힌다 — 로그에서 그 사실은 눈에 띄지 않는다. 지금 도메인은 여행 레퍼런스 데이터라 개인정보가
 * 없고 사용자 계정도 임시 Basic 하나뿐이다(#122). OAuth 로 실사용자가 들어오면 이 규칙을 다시 본다.
 */
public final class SensitiveParams {

    /** 이름이 이 중 하나면 값을 가린다. 비교는 소문자로 한다. */
    private static final Set<String> MASKED_NAMES = Set.of("servicekey", "appkey", "password", "token");

    private static final String MASK = "***";

    /** 사람이 읽는 목록 구분자 — 쿼리스트링의 {@code &} 보다 눈에 덜 걸린다. */
    private static final String READABLE_DELIMITER = ", ";

    /**
     * 값 하나의 표시 상한. 넘으면 잘라내고 표식을 붙인다.
     *
     * <p>로그 한 줄은 훑어보는 것이라 긴 값 하나가 줄 전체를 밀어내면 나머지를 못 읽는다. 전문이 필요하면
     * 요청 본문·추적 id 로 찾는다.
     */
    private static final int MAX_VALUE_LENGTH = 60;

    private static final String TRUNCATED = "…";

    /**
     * 로그에 그대로 실으면 안 되는 문자 — 제어문자 전부.
     *
     * <p><b>디코딩하는 순간 생기는 위험이다.</b> {@code %0A} 가 실제 개행이 되면 값 하나가 로그를 여러 줄로
     * 쪼개, 공격자가 가짜 로그 줄을 지어낼 수 있다. 인코딩된 채로 찍을 때는 없던 문제라 디코딩과 함께 막는다.
     */
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final String PAIR_DELIMITER = "&";
    private static final String NAME_VALUE_DELIMITER = "=";
    private static final int NAME_VALUE_LIMIT = 2;

    private SensitiveParams() {}


    /**
     * 이름 비교용 정규화. {@code request.getQueryString()} 은 디코딩되지 않은 원문이라
     * {@code t%6Fken} 처럼 퍼센트 인코딩된 이름이 {@code toLowerCase} 만으로는 매칭을 피해간다.
     * 앞뒤 공백도 마찬가지로 매칭을 방해할 수 있어 함께 걷어낸다.
     *
     * <p>{@code URLDecoder.decode} 는 잘못된 {@code %} 시퀀스에 {@link IllegalArgumentException}
     * 을 던진다. 로그를 찍다가 요청 처리가 죽으면 안 되므로, 디코딩이 실패하면 원문 이름으로
     * 비교를 이어간다(마스킹 여부만 원문 기준으로 보수적으로 판단하게 된다).
     */
    private static String normalizeName(String rawName) {
        try {
            return URLDecoder.decode(rawName, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return rawName.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 사람이 읽을 수 있는 파라미터 목록 — 로그용.
     *
     * <p>{@code request.getQueryString()} 은 인코딩된 원문이라 한글이 {@code %ec%b6%a9...} 으로 나가 눈으로
     * 못 읽는다. 디코딩해서 {@code region=충청남도} 로 보여준다.
     *
     * <p>마스킹 규칙은 그대로 탄다 — 읽기 좋게 만드느라 가려야 할 값을 드러내면 안 된다.
     *
     * @return {@code "region=충청남도, kind=FOOD"}. 파라미터가 없으면 빈 문자열
     */
    public static String readableParams(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return Arrays.stream(query.split(PAIR_DELIMITER))
                .map(SensitiveParams::readablePair)
                .filter(pair -> !pair.isEmpty())
                .collect(Collectors.joining(READABLE_DELIMITER));
    }

    private static String readablePair(String pair) {
        String[] parts = pair.split(NAME_VALUE_DELIMITER, NAME_VALUE_LIMIT);
        String name = forLog(parts[0]);
        if (parts.length < NAME_VALUE_LIMIT) {
            return name; // 등호 없는 조각 — 이름만 남긴다
        }
        if (MASKED_NAMES.contains(normalizeName(parts[0]))) {
            return name + NAME_VALUE_DELIMITER + MASK;
        }
        return name + NAME_VALUE_DELIMITER + forLog(parts[1]);
    }

    /** 디코딩 → 제어문자 제거 → 길이 제한. 이 순서를 지켜야 디코딩으로 생긴 개행이 걸러진다. */
    private static String forLog(String raw) {
        String decoded;
        try {
            decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            decoded = raw; // 깨진 인코딩 — 로그를 찍다가 요청을 죽이지 않는다
        }
        String safe = CONTROL_CHARS.matcher(decoded).replaceAll("");
        return safe.length() <= MAX_VALUE_LENGTH ? safe : safe.substring(0, MAX_VALUE_LENGTH) + TRUNCATED;
    }
}
