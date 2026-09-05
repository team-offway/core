package com.offway.core.common.external;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 요청 <b>하나</b>가 태운 외부 호출(#421).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>한도 알림은 총량이 10% 단계를 넘을 때만 운다(#257). 주체 내역(#285)이 붙어 "배치 603 · 코스생성
 * 97" 까지는 갈렸지만 여전히 <b>하루치 뭉텅이</b>다. 정작 알고 싶은 것은 <b>코스 하나에 몇 건이
 * 나가나</b> 인데, 그 숫자가 없으면 "한도가 몇 명분인가" 를 나눗셈조차 못 한다.
 *
 * <h2>DB 에 안 남긴다</h2>
 *
 * <p>요청이 끝나면 알림 한 줄로 나가고 버려진다. 날짜별 총량은 {@code external_api_call} 이 이미
 * 들고 있어, 표를 늘릴 이유가 없다.
 *
 * <h2>스레드 안전 — 여기가 요점이다</h2>
 *
 * <p>코스 생성이 가장 많이 태우는 경로가 <b>병렬 팬아웃</b>이라, 이 객체는 여러 스레드가 동시에
 * 올린다. 그래서 두 가지를 함께 지킨다.
 *
 * <ul>
 *   <li><b>맵을 미리 채운다.</b> 생성 시점에 모든 API 자리를 만들어 두면 이후로는 구조 변경이 없어
 *       읽기·쓰기가 동시에 일어나도 안전하다. {@code computeIfAbsent} 로 늦게 만들면 그 순간이
 *       경쟁 구간이 된다.
 *   <li><b>{@link LongAdder} 를 쓴다.</b> 경합이 있을 때 {@code AtomicLong} 보다 낫고, 여기서
 *       필요한 것은 정확한 순간값이 아니라 <b>끝난 뒤의 합계</b>다.
 * </ul>
 *
 * <p>{@link EnumMap} 인 것도 이유가 있다 — 선언 순서가 고정이라 <b>알림 줄의 순서가 매번 같다.</b>
 * 순서가 흔들리면 두 알림을 눈으로 비교할 수 없다.
 */
public final class RequestUsage {

    private final Map<ExternalApi, LongAdder> counts;

    public RequestUsage() {
        Map<ExternalApi, LongAdder> prepared = new EnumMap<>(ExternalApi.class);
        Arrays.stream(ExternalApi.values()).forEach(api -> prepared.put(api, new LongAdder()));
        this.counts = prepared;
    }

    /** 한 건 올린다. 팬아웃 스레드가 동시에 부른다. */
    public void record(ExternalApi api) {
        LongAdder counter = counts.get(api);
        if (counter == null) {
            // 열거형 상수는 생성자에서 전부 채웠으므로 여기 닿을 수 없다. 닿는다면 코드 버그다.
            throw new IllegalStateException("모르는 외부 API 입니다: " + api);
        }
        counter.increment();
    }

    /**
     * 실제로 부른 것만, <b>선언 순서대로</b>.
     *
     * <p>0 인 API 는 담지 않는다 — 알림 줄에 "TAGO 여객선 0" 이 열 줄 붙으면 정작 봐야 할 숫자가 묻힌다.
     */
    public Map<ExternalApi, Long> snapshot() {
        Map<ExternalApi, Long> used = new LinkedHashMap<>();
        counts.forEach((api, counter) -> {
            long value = counter.sum();
            if (value > 0) {
                used.put(api, value);
            }
        });
        // Map.copyOf 가 아니다 — 그쪽은 순회 순서를 보장하지 않아, 순서를 지키려고 EnumMap 을 쓴 것이
        // 이 한 줄에서 무너진다. 알림 두 개를 눈으로 비교하려면 순서가 매번 같아야 한다.
        return Collections.unmodifiableMap(used);
    }

    public long total() {
        return counts.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** 한 건도 안 나갔나 — <b>정상이다.</b> 전부 캐시·DB 로 끝난 요청이라는 뜻이다. */
    public boolean isEmpty() {
        return total() == 0;
    }
}
