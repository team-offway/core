package com.offway.core.transport.domain;

/**
 * 실호출로 잰 구간 하나(#107 · #97) — 버스·여객선 어댑터가 돌려주는 값.
 *
 * <p><b>시각이 아니라 소요시간이다.</b> 조회창이 오늘~+2일(여객선 +7일)뿐이라 미래 날짜의 출발·도착 시각은
 * 애초에 물을 수 없다. 대신 소요시간에는 편차가 없어(실측 2026-08-31) 한 번 재면 그대로 쓸 수 있다.
 *
 * @param minutes 소요시간(분)
 * @param charge 요금(원, 모르면 null)
 * @param vehicleName 등급·선명(우등 · 엘도라도익스프레스호, 모르면 null)
 */
public record MeasuredLeg(int minutes, Integer charge, String vehicleName) {

    public MeasuredLeg {
        if (minutes <= 0) {
            // 0분 이동은 없다. 시각 파싱이 어긋났거나 응답이 뒤집힌 것이라 값으로 받아들이면 안 된다.
            throw new IllegalArgumentException("소요시간은 양수여야 합니다: " + minutes);
        }
    }
}
