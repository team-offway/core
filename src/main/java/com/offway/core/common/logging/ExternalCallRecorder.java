package com.offway.core.common.logging;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 요청 하나가 부른 외부 API 의 횟수와 누적시간을 모은다.
 *
 * <p><b>스레드 안전해야 한다.</b> WebClient 필터의 완료 콜백은 리액터 스레드에서 도는데, 어댑터가
 * {@code .block()} 으로 부르므로 서블릿 스레드와 리액터 스레드가 같은 수집기를 만진다. 병렬 팬아웃까지
 * 겹치면 여러 스레드가 동시에 적는다.
 *
 * <p>{@code tour 840ms×3} 에서 840ms 는 <b>누적</b>이고 ×3 은 횟수다. "1.28초 중 1.05초가 외부" 를
 * 읽으려면 누적이어야 한다.
 */
public final class ExternalCallRecorder {

    private static final String ENTRY_DELIMITER = " · ";
    private static final String SINGLE_FORMAT = "%s %dms";
    private static final String REPEATED_FORMAT = "%s %dms×%d";

    private final Map<String, Accumulator> bySystem = new ConcurrentHashMap<>();

    public void record(String system, long millis) {
        bySystem.computeIfAbsent(system, key -> new Accumulator()).add(millis);
    }

    public boolean isEmpty() {
        return bySystem.isEmpty();
    }

    /** 누적시간 내림차순 — 느린 쪽이 먼저 눈에 걸리게 한다. */
    public String summary() {
        return bySystem.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Accumulator> e) -> e.getValue().totalMillis())
                        .reversed())
                .map(entry -> describe(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(ENTRY_DELIMITER));
    }

    private static String describe(String system, Accumulator accumulator) {
        long total = accumulator.totalMillis();
        long count = accumulator.count();
        if (count == 1) {
            return SINGLE_FORMAT.formatted(system, total);
        }
        return REPEATED_FORMAT.formatted(system, total, count);
    }

    /** 시스템 하나의 누적시간·횟수. 합계·카운트를 각각 원자적으로 올린다. */
    private static final class Accumulator {

        private final AtomicLong totalMillis = new AtomicLong();
        private final AtomicLong count = new AtomicLong();

        void add(long millis) {
            totalMillis.addAndGet(millis);
            count.incrementAndGet();
        }

        long totalMillis() {
            return totalMillis.get();
        }

        long count() {
            return count.get();
        }
    }
}
