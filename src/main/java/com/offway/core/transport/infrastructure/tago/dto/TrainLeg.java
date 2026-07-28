package com.offway.core.transport.infrastructure.tago.dto;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 한 열차편의 출발·도착·소요시간(TAGO 열차정보). 코스 대중교통 구간의 실제 이동시간 근거로 쓴다. KTX·무궁화 등 등급을 함께 담아
 * UI 가 "KTX 1시간 40분"처럼 보여줄 수 있게 한다.
 *
 * @param trainType 열차 등급명(KTX·ITX·무궁화 등)
 * @param departAt 출발 일시
 * @param arriveAt 도착 일시
 * @param durationMinutes 소요시간(분) — 출발·도착에서 도출
 */
public record TrainLeg(String trainType, LocalDateTime departAt, LocalDateTime arriveAt, int durationMinutes) {

    /** 출발·도착 시각에서 소요시간을 계산해 만든다(계산이면 팩토리 — 외부가 durationMinutes 를 직접 세팅해 불변식이 깨지지 않게). */
    public static TrainLeg of(String trainType, LocalDateTime departAt, LocalDateTime arriveAt) {
        if (departAt == null || arriveAt == null) {
            throw new IllegalArgumentException("출발·도착 시각은 필수입니다");
        }
        if (arriveAt.isBefore(departAt)) {
            throw new IllegalArgumentException("도착이 출발보다 앞설 수 없습니다");
        }
        return new TrainLeg(trainType, departAt, arriveAt, (int) Duration.between(departAt, arriveAt).toMinutes());
    }
}
