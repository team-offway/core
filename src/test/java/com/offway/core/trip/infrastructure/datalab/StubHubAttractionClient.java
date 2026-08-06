package com.offway.core.trip.infrastructure.datalab;

import com.offway.core.trip.infrastructure.datalab.dto.HubAttractionItem;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * {@link HubAttractionClient} 외부 경계 stub — 통합 테스트에서 중심 관광지 호출을 격리한다. default 는 throw 라
 * 명시 세팅을 빠뜨리면 즉시 깨진다.
 */
public class StubHubAttractionClient implements HubAttractionClient {

    private BiFunction<String, YearMonth, List<HubAttractionItem>> behavior = (legalCode, month) -> {
        throw new IllegalStateException("StubHubAttractionClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    /** 호출된 (법정코드, 기준월) 기록 — 이미 최신일 때 외부를 안 부르는지 검증용. */
    private final List<String> calls = new ArrayList<>();

    public void respond(BiFunction<String, YearMonth, List<HubAttractionItem>> behavior) {
        this.behavior = behavior;
        this.calls.clear();
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public List<HubAttractionItem> findByRegion(String legalCode, YearMonth baseMonth, int rows) {
        calls.add(legalCode + "@" + baseMonth);
        return behavior.apply(legalCode, baseMonth);
    }
}
