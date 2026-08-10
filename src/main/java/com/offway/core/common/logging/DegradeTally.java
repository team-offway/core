package com.offway.core.common.logging;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * degrade 를 <b>사유별</b>로 센다 — 로그 줄 수는 사유의 가짓수만큼만, 요약은 건수까지.
 *
 * <p><b>왜 필요한가.</b> 89개 지역 팬아웃이 외부 실패로 degrade 하면 지역마다 한 줄씩 WARN 이 나갔다.
 * 39줄이 사실상 같은 말인데다, 정작 알아야 할 "몇 건이 같은 이유였나" 에는 아무도 답하지 못했다 —
 * 39건이 전부 429 인지, 그 안에 timeout 이 섞였는지 로그로는 구분이 안 됐다(#224).
 *
 * <p>그렇다고 개별 로그를 전부 지우면 규약("degrade 해서 넘어갈 때도 왜 degrade 했는지 남긴다. 폴백이
 * 정상처럼 보이면 장애를 아무도 모른다")과 어긋난다. 그래서 <b>사유마다 첫 건만</b> 남긴다 —
 * {@link #add} 가 그 판정을 돌려준다.
 *
 * <p>팬아웃이 병렬이라 동시 호출을 전제로 만든다. 카운터가 경쟁하면 요약이 조용히 틀리는데, 요약이
 * 틀리면 "degrade 가 없었다" 는 잘못된 안심을 준다.
 */
public final class DegradeTally {

    private static final String ENTRY_DELIMITER = ", ";

    private static final String NAME_VALUE_DELIMITER = "=";

    /** 사유(집계 키) → 건수. 키는 {@link RootCause#label} 이 만든 짧은 라벨이라 가짓수가 제한된다. */
    private final Map<String, AtomicInteger> byReason = new ConcurrentHashMap<>();

    private final AtomicInteger total = new AtomicInteger();

    /**
     * 한 건을 세고, <b>이 사유가 처음인지</b> 돌려준다.
     *
     * <p>기록과 판정을 한 번에 하는 이유는 병렬이기 때문이다. "세고 나서 물어보기" 로 나누면 두 스레드가
     * 동시에 첫 건이라 판단해 같은 사유가 두 줄 남는다.
     *
     * @return 이 사유의 첫 건이면 참 — 호출부는 이때만 WARN 으로 남긴다
     */
    public boolean add(String reason) {
        total.incrementAndGet();
        return byReason.computeIfAbsent(reason, key -> new AtomicInteger()).incrementAndGet() == 1;
    }

    /** 사유를 가리지 않은 전체 건수. */
    public int total() {
        return total.get();
    }

    /** {@code 429=39, TimeoutException=1} — 많은 사유부터. 아무것도 없으면 빈 문자열. */
    public String summary() {
        return byReason.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, AtomicInteger>>comparingInt(entry -> entry.getValue().get())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + NAME_VALUE_DELIMITER + entry.getValue().get())
                .collect(Collectors.joining(ENTRY_DELIMITER));
    }
}
