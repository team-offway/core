package com.offway.core.transport.infrastructure.kakao;

import com.offway.core.transport.infrastructure.kakao.dto.FoundPlace;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 주소·장소 검색 stub — 외부 HTTP 경계만 격리한다(테스트 규약).
 *
 * <p><b>기본 람다가 던진다.</b> 명시 세팅을 빠뜨리면 즉시 깨지게 해, 이전 테스트의 상태가 살아남아
 * 통과하는 함정을 막는다.
 */
public class StubOriginPlaceSearchClient implements OriginPlaceSearchClient {

    private BiFunction<String, Integer, List<FoundPlace>> handler = (query, limit) -> {
        throw new IllegalStateException("주소 검색 stub 응답을 세팅하지 않았습니다");
    };

    public void willReturn(List<FoundPlace> places) {
        this.handler = (query, limit) -> places;
    }

    /** 키가 없을 때의 실제 동작 — 외부를 부르지 않고 빈 목록. */
    public void willReturnNothing() {
        this.handler = (query, limit) -> List.of();
    }

    /** 부르면 깨지게 둔다 — "외부를 부르지 않는다" 를 단언하는 테스트가 쓴다. */
    public void willThrow() {
        this.handler = (query, limit) -> {
            throw new IllegalStateException("이 시나리오에서는 외부를 부르지 않아야 합니다 query=" + query.length());
        };
    }

    public void willAnswer(BiFunction<String, Integer, List<FoundPlace>> handler) {
        this.handler = handler;
    }

    @Override
    public List<FoundPlace> search(String query, int limit) {
        return handler.apply(query, limit);
    }
}
