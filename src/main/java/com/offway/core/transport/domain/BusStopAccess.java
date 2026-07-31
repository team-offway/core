package com.offway.core.transport.domain;

import java.util.List;

/**
 * 어떤 좌표 주변의 버스 정류소 조회 결과 — 네 상태를 <b>구분</b>한다. "정류소가 없음"·"데이터가 없음"·"조회가 실패"는 UX 가
 * 완전히 다르기 때문이다(닫힌 계층이라 {@code sealed} + 패턴 매칭).
 *
 * <p>인구감소지역은 실제로 정류소가 없는 곳이 흔하다. 그래서 {@link NoStopNearby} 는 오류가 아니라 <b>말해줄 가치가 있는
 * 정상 결과</b>다 — "여기는 대중교통으로 가기 어려워요, 자차를 권해요" 로 이어진다. 반대로 {@link Unavailable} 은
 * 사용자에게 알릴 게 아니라 조용히 접근성 판단을 생략해야 한다.
 *
 * <p>{@link NotCovered} 를 따로 두는 이유가 이 구분의 핵심이다. TAGO 가 담지 않는 지자체(정선·평창 등)를
 * {@code NoStopNearby} 로 뭉뚱그리면 <b>"정선에 버스가 없다"는 틀린 안내</b>가 나간다. 정선에 버스는 다닌다 — 우리가
 * 데이터를 못 얻을 뿐이다.
 *
 * <ul>
 *   <li>{@link Available} — 주변 정류소가 있다(가까운 순).
 *   <li>{@link NoStopNearby} — 조회 정상, 주변에 정류소 없음 → 대중교통 접근이 어렵다고 안내 가능.
 *   <li>{@link NotCovered} — TAGO 미커버 지자체 → "대중교통 정보 없음" 으로 안내(≠ 버스 없음).
 *   <li>{@link Unavailable} — 조회 불가(키 없음·호출 실패) → 조용히 판단 생략.
 * </ul>
 */
public sealed interface BusStopAccess {

    /** 주변 정류소 목록(가까운 순). 비어 있을 수 없다 — 비면 {@link NoStopNearby} 다. */
    record Available(List<BusStop> stops) implements BusStopAccess {

        public Available {
            if (stops == null || stops.isEmpty()) {
                throw new IllegalArgumentException("정류소가 하나 이상이어야 합니다. 비었으면 NoStopNearby 입니다");
            }
            stops = List.copyOf(stops);
        }

        /** 가장 가까운 정류소 — 응답이 근접순이라 첫 항목이다. */
        public BusStop nearest() {
            return stops.getFirst();
        }
    }

    /** 조회 정상, 주변에 정류소 없음 — 커버되는 지자체에서 나온 결과라 "대중교통이 어렵다" 고 안내해도 된다. */
    record NoStopNearby() implements BusStopAccess {}

    /**
     * TAGO 가 담지 않는 지자체 — 정류소가 없는 게 아니라 데이터가 없다.
     *
     * <p>우리 89곳 중 13곳(정선·평창·영월·삼척·양구·화천·강원 고성·예산·강진·담양·보성·영광·화순)이 여기 걸린다.
     * 공공데이터 폴백이 없어(같은 모집단) 메울 수 없으므로, 메우는 대신 정직하게 드러낸다.
     */
    record NotCovered() implements BusStopAccess {}

    /** 조회 불가(키 없음·호출 실패) — 조용히 폴백. */
    record Unavailable() implements BusStopAccess {}
}
