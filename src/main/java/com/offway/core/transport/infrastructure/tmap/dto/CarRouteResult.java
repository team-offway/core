package com.offway.core.transport.infrastructure.tmap.dto;

import com.offway.core.transport.domain.UnroutableReason;

/**
 * 자동차 경로 조회 결과(#335) — <b>실패를 한 덩어리로 뭉개지 않는다.</b>
 *
 * <p>예전에는 {@code Optional<TmapRoute>} 하나였다. 그러면 "좌표가 도로에 안 붙는다" 와 "타임아웃"·"키 없음"
 * 이 같은 빈 값이 되어, 상위가 무엇을 기억해야 할지 정할 수 없었다. 좌표 탓으로 장소를 빼는 판단은
 * <b>좌표 탓일 때만</b> 해야 한다 — 타임아웃으로 빼면 멀쩡한 곳이 영구히 사라진다.
 */
public sealed interface CarRouteResult {

    /** 실측 경로를 받았다. */
    record Found(TmapRoute route) implements CarRouteResult {
    }

    /** TMAP 이 <b>좌표를 문제 삼아</b> 거절했다. 이 구간은 앞으로도 같은 이유로 실패한다. */
    record Rejected(UnroutableReason reason) implements CarRouteResult {
    }

    /**
     * 이번에는 못 받았다 — 키 없음·타임아웃·한도 소진·응답 형식 이상.
     *
     * <p>좌표 탓이 아니므로 <b>아무것도 기억하지 않는다.</b> 다음 호출에는 성공할 수 있다.
     */
    record Unavailable() implements CarRouteResult {

        private static final Unavailable INSTANCE = new Unavailable();

        public static Unavailable instance() {
            return INSTANCE;
        }
    }
}
