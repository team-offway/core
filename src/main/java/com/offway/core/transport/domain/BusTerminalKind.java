package com.offway.core.transport.domain;

/**
 * 버스 터미널 종류(#107·#97) — 고속버스와 시외버스는 <b>다른 API·다른 코드 공간</b>을 쓴다.
 *
 * <p>실측(2026-08-05)으로 확인했다. 코드 접두사가 다르고 452개와 337개 사이에 <b>겹치는 코드가 하나도 없다</b>.
 * 그래서 한 테이블에 담되 종류로 가른다 — 최근접 터미널을 찾은 뒤 어느 API 로 구간을 물을지 이 값이 정한다.
 *
 * <table>
 *   <caption>종류별 차이</caption>
 *   <tr><th></th><th>고속버스</th><th>시외버스</th></tr>
 *   <tr><td>터미널 수</td><td>452</td><td>337</td></tr>
 *   <tr><td>코드 예</td><td>{@code NAEK010}</td><td>{@code NAI0511601}</td></tr>
 *   <tr><td>목록이 주는 것</td><td>코드·이름</td><td>코드·이름·<b>소재지</b></td></tr>
 * </table>
 *
 * <p><b>둘 다 필요하다.</b> 우리 89개 인구감소지역 중 고속버스로 닿는 곳은 61곳뿐이다 — 경남·경북 군 지역과
 * 광역시 자치구는 고속버스가 다니지 않아 시외버스로만 갈 수 있다.
 */
public enum BusTerminalKind {

    /** 고속버스 — {@code ExpBusInfo}. 주요 도시를 잇는다. */
    EXPRESS("고속버스"),

    /** 시외버스 — {@code SuburbsBusInfo}. 군 단위까지 촘촘히 닿는다. */
    INTERCITY("시외버스");

    private final String label;

    BusTerminalKind(String label) {
        this.label = label;
    }

    /** 화면 노출 한글 라벨. */
    public String label() {
        return label;
    }
}
