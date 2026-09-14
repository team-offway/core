package com.offway.core.trip.infrastructure.crowd;

import com.offway.core.trip.infrastructure.crowd.dto.AttractionCrowd;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * {@link AttractionCrowdClient} 외부 경계 stub — 통합 테스트에서 집중률 호출을 격리한다.
 *
 * <p>default 는 throw 라 명시 세팅을 빠뜨리면 즉시 깨진다.
 *
 * <p><b>지역을 받는 람다다.</b> 이 배치는 89곳을 하나씩 도는데 <b>지역마다 결과가 갈리는 회차</b>가
 * 설계의 일부라(예보가 없는 곳 16곳 · 호출이 실패하는 곳), 그 분기를 테스트가 만들 수 있어야 한다.
 */
public class StubAttractionCrowdClient implements AttractionCrowdClient {

    private Function<String, List<AttractionCrowd>> behavior = legalCode -> {
        throw new IllegalStateException(
                "StubAttractionCrowdClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(Function<String, List<AttractionCrowd>> behavior) {
        this.behavior = behavior;
    }

    @Override
    public List<AttractionCrowd> findByRegion(String legalCode, Duration maxWait) {
        return behavior.apply(legalCode);
    }
}
