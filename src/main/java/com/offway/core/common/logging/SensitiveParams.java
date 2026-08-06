package com.offway.core.common.logging;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
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
    private static final String PAIR_DELIMITER = "&";
    private static final String NAME_VALUE_DELIMITER = "=";
    private static final int NAME_VALUE_LIMIT = 2;

    private SensitiveParams() {}

    public static String maskQueryString(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return Arrays.stream(query.split(PAIR_DELIMITER))
                .map(SensitiveParams::maskPair)
                .collect(Collectors.joining(PAIR_DELIMITER));
    }

    private static String maskPair(String pair) {
        String[] parts = pair.split(NAME_VALUE_DELIMITER, NAME_VALUE_LIMIT);
        if (parts.length < NAME_VALUE_LIMIT) {
            return pair;
        }
        if (MASKED_NAMES.contains(parts[0].toLowerCase(Locale.ROOT))) {
            return parts[0] + NAME_VALUE_DELIMITER + MASK;
        }
        return pair;
    }
}
