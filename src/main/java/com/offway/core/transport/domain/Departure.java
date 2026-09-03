package com.offway.core.transport.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 그 구간의 <b>한 편</b> — 몇 시 차인가(#414).
 *
 * <p>교통 카드가 "약 2시간 29분" 까지만 말하면 사용자가 다음에 할 일(표 끊기)을 다른 앱에서 다시 찾아야
 * 한다. 몇 시에 뜨는 편이 있는지가 그 자리를 메운다.
 *
 * <p><b>수단을 가리지 않는다.</b> 열차의 {@link TrainLeg} 와 버스·여객선의 원본이 같은 모양(출발·도착·등급)
 * 인데 타입이 갈려 있어, 화면에 올릴 때마다 둘을 따로 옮겨야 했다.
 *
 * <p>소요시간은 컴포넌트로 두지 않는다 — 출발·도착에서 도출되는 값이라 따로 받으면 어긋난 값이 들어온다.
 *
 * @param vehicleType 등급·편명(KTX · 무궁화 · 우등 등). <b>없을 수 있다</b> — 여객선처럼 등급 개념이 없거나
 *     응답이 안 주는 수단이다
 * @param departAt 출발 일시
 * @param arriveAt 도착 일시
 */
public record Departure(String vehicleType, LocalDateTime departAt, LocalDateTime arriveAt) {

    /**
     * 화면에 실을 편 수 상한.
     *
     * <p>TAGO 는 한 구간에 최대 50편까지 준다. 그대로 내리면 교통 카드 하나가 화면을 넘긴다 — 사용자가
     * 보는 것은 "지금 이후에 탈 수 있는 몇 편" 이지 하루 시간표 전체가 아니다.
     */
    public static final int MAX_SHOWN = 6;

    public Departure {
        Objects.requireNonNull(departAt, "출발 시각은 필수입니다");
        Objects.requireNonNull(arriveAt, "도착 시각은 필수입니다");
        if (!arriveAt.isAfter(departAt)) {
            throw new IllegalArgumentException("도착이 출발보다 엄격히 이후여야 합니다(0분 이동 불가)");
        }
    }

    /** 소요시간(분) — 출발·도착에서 도출한 파생값. */
    public int durationMinutes() {
        return (int) Duration.between(departAt, arriveAt).toMinutes();
    }

    /**
     * 집을 나서는 시각 이후에 떠나는 편만, <b>이른 순으로</b> 상한까지.
     *
     * <p>정렬이 소요시간이 아니라 출발 시각인 것이 {@code fastest} 와 다른 점이다. 화면은 시간표라서
     * "다음 차가 몇 시인가" 가 먼저다 — 가장 빠른 편 하나는 이미 카드 위쪽이 말하고 있다.
     *
     * <p>막차가 지나 <b>빈 목록</b>이 나올 수 있다. 반반차로 15시에 나서는데 그 지역 막차가 14시면 그렇고,
     * 그건 정상 결과다 — 화면이 시간표 줄만 접는다.
     */
    public static List<Departure> upcoming(List<Departure> all, LocalTime notBefore) {
        return all.stream()
                .filter(departure -> !departure.departAt().toLocalTime().isBefore(notBefore))
                .sorted(Comparator.comparing(Departure::departAt))
                .limit(MAX_SHOWN)
                .toList();
    }
}
