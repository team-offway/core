package com.offway.core.transport.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * TMAP 이 <b>좌표 자체를 문제 삼아</b> 거절한 사유(#335).
 *
 * <p><b>일시적 실패와 갈라야 한다.</b> timeout·한도 소진·5xx 는 좌표 탓이 아니므로 그걸로 장소를 빼면
 * 멀쩡한 곳이 영구히 사라진다. 여기 든 코드만 "이 좌표로는 앞으로도 경로를 못 만든다" 는 뜻이다.
 */
public enum UnroutableReason {

    /**
     * {@code 1100} — 근처에 도로 링크가 없다.
     *
     * <p>운영에서 실제로 걸린 것은 <b>귀목봉</b>(해발 1,036m 산 정상)이었다. TMAP 이 좌표를 도로에
     * 스냅하려는데 붙일 도로가 없다. 이름으로는 못 거른다 — "봉" 으로 끝나는 장소 30건을 표본으로 재봤을 때
     * 걸린 것은 0건이었다.
     */
    NO_ROAD_LINK("1100"),

    /**
     * {@code 1009} — 좌표가 한반도 범위를 벗어났다.
     *
     * <p>지오코딩이 틀린 경우다. 울릉군으로 적재된 장소 3건이 경도 130.9 가 아니라 <b>128.87</b>(동해
     * 한복판)을 들고 있었다.
     */
    OUT_OF_BOUNDS("1009");

    private final String tmapCode;

    UnroutableReason(String tmapCode) {
        this.tmapCode = tmapCode;
    }

    public String tmapCode() {
        return tmapCode;
    }

    /** 응답 본문의 code 가 좌표 탓인 사유인가. 아니면 빈 값 — 일시적 실패로 다룬다. */
    public static Optional<UnroutableReason> fromTmapCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String trimmed = code.trim();
        return Arrays.stream(values()).filter(reason -> reason.tmapCode.equals(trimmed)).findFirst();
    }
}
