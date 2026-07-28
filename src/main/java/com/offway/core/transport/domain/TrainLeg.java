package com.offway.core.transport.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 한 열차편의 출발·도착(TAGO 열차정보). 코스 대중교통 구간의 실제 이동시간 근거로 쓴다. KTX·무궁화 등 등급을 함께 담아 UI 가
 * "KTX 1시간 40분"처럼 보여줄 수 있게 한다.
 *
 * <p>소요시간은 record 컴포넌트로 두지 않고 출발·도착에서 계산하는 파생값({@link #durationMinutes()})으로 둔다 — 컴포넌트면
 * canonical 생성자가 그대로 받아 잘못된 소요시간이 들어갈 수 있다. 불변식(시각 필수·역전 금지)은 생성자가 검증한다.
 *
 * @param trainType 열차 등급명(KTX·ITX·무궁화 등)
 * @param departAt 출발 일시
 * @param arriveAt 도착 일시
 */
public record TrainLeg(String trainType, LocalDateTime departAt, LocalDateTime arriveAt) {

    public TrainLeg {
        if (departAt == null || arriveAt == null) {
            throw new IllegalArgumentException("출발·도착 시각은 필수입니다");
        }
        if (!arriveAt.isAfter(departAt)) {
            throw new IllegalArgumentException("도착이 출발보다 엄격히 이후여야 합니다(0분 이동 불가)");
        }
    }

    public static TrainLeg of(String trainType, LocalDateTime departAt, LocalDateTime arriveAt) {
        return new TrainLeg(trainType, departAt, arriveAt);
    }

    /** 소요시간(분) — 출발·도착에서 도출한 파생값. */
    public int durationMinutes() {
        return (int) Duration.between(departAt, arriveAt).toMinutes();
    }
}
