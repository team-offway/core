package com.offway.core.common.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 한 API 의 오늘 소비를 주체별로 갈라 한 줄로 만든다(#285).
 *
 * <p><b>알림 개수를 늘리지 않는 것이 전제다.</b> #257 이 보내는 그 메시지 안에 줄 하나를 붙일 뿐이라,
 * 길이가 주체 수만큼 자라면 안 된다. 상위 몇 개만 싣고 나머지는 하나로 접는다.
 *
 * @param shares 많이 쓴 순 상위 주체
 * @param othersCount 접힌 나머지의 호출 수 합
 * @param othersCallers 접힌 나머지의 주체 수
 */
public record CallerBreakdown(List<Share> shares, long othersCount, int othersCallers) {

    /** 한 줄에 실을 주체 수. 넘으면 나머지를 "그 외" 로 접는다. */
    private static final int MAX_SHARES = 4;

    private static final String SEPARATOR = " · ";

    /** 주체 하나의 몫. */
    public record Share(String caller, long count) {
    }

    public CallerBreakdown {
        shares = List.copyOf(shares);
    }

    /**
     * 주체별 호출 수에서 만든다.
     *
     * <p>입력이 많이 쓴 순으로 정렬돼 있다고 가정하지 않는다 — 정렬을 저장소에만 맡기면 호출 경로가 하나
     * 늘 때마다 같은 규칙을 다시 지켜야 한다.
     */
    public static CallerBreakdown of(Map<String, Long> counts) {
        List<Share> sorted = counts.entrySet().stream()
                .map(entry -> new Share(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> {
                    int byCount = Long.compare(right.count(), left.count());
                    return byCount != 0 ? byCount : left.caller().compareTo(right.caller());
                })
                .toList();
        if (sorted.size() <= MAX_SHARES) {
            return new CallerBreakdown(sorted, 0, 0);
        }
        List<Share> top = new ArrayList<>(sorted.subList(0, MAX_SHARES));
        List<Share> rest = sorted.subList(MAX_SHARES, sorted.size());
        return new CallerBreakdown(top, rest.stream().mapToLong(Share::count).sum(), rest.size());
    }

    public boolean isEmpty() {
        return shares.isEmpty();
    }

    /** 한 줄로. 실을 것이 없으면 빈 문자열이라 호출부가 그대로 이어 붙여도 된다. */
    public String describe() {
        if (isEmpty()) {
            return "";
        }
        String top = shares.stream()
                .map(share -> "%s %d".formatted(share.caller(), share.count()))
                .collect(Collectors.joining(SEPARATOR));
        return othersCallers == 0
                ? top
                : top + SEPARATOR + "그 외 %d곳 %d".formatted(othersCallers, othersCount);
    }
}
