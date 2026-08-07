package com.offway.core.common.logging;

import java.util.List;

/**
 * 응답 목록을 로그 한 줄에 실을 크기로 줄인다.
 *
 * <p><b>건수만 남긴다.</b> 처음에는 대표 5건을 함께 냈지만(`숙소=42건 (LIC-1234 …외 37건)`), 운영 로그에서
 * 사용자 흐름을 훑는 데는 "무엇을 몇 건 받았나" 로 충분했고 목록이 줄 길이를 두 배로 만들었다. 어떤 항목이
 * 나갔는지가 필요한 순간은 드물고, 그때는 어차피 id 로 다시 조회한다.
 */
public final class LogSummaries {

    private static final String COUNT_FORMAT = "%s %d건";

    private LogSummaries() {}

    /** 예: {@code 숙소 42건}. {@code null}·빈 목록은 {@code 0건}. */
    public static String count(String label, List<?> items) {
        return COUNT_FORMAT.formatted(label, items == null ? 0 : items.size());
    }
}
