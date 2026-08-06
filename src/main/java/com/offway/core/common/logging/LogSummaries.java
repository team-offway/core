package com.offway.core.common.logging;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 목록을 로그 한 줄에 들어갈 크기로 줄인다.
 *
 * <p><b>잘렸다는 사실을 목록 괄호 안에 박는다.</b> 앞에 {@code 20건} 이 있어도 눈은 괄호 안을 먼저 읽어
 * 5건만 나간 것으로 굳는다. {@code …외 15건} 이 있으면 5 + 15 = 20 이 그 자리에서 검산된다.
 *
 * <p>괄호는 두 종류를 쓴다 — 바깥 {@code [...]} 은 요약 전체(필터가 붙인다), 안쪽 {@code (...)} 은 목록이다.
 * 같은 괄호를 겹치면 어디까지가 목록인지 눈으로 안 갈린다.
 */
public final class LogSummaries {

    private static final int MAX_ITEMS = 5;
    private static final String ITEM_DELIMITER = " ";
    private static final String SUMMARY_FORMAT = "%s=%d건 (%s)";
    private static final String TRUNCATION_FORMAT = "…외 %d건";

    private LogSummaries() {}

    public static <T> String list(String label, List<T> items, Function<T, String> describe) {
        if (items == null || items.isEmpty()) {
            return SUMMARY_FORMAT.formatted(label, 0, "");
        }

        String shown = items.stream().limit(MAX_ITEMS).map(describe).collect(Collectors.joining(ITEM_DELIMITER));

        int hidden = items.size() - Math.min(items.size(), MAX_ITEMS);
        if (hidden > 0) {
            shown = shown + ITEM_DELIMITER + TRUNCATION_FORMAT.formatted(hidden);
        }
        return SUMMARY_FORMAT.formatted(label, items.size(), shown);
    }
}
