package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 법정 시군구코드별 관광객 집계 — 관광빅데이터에서 받아 <b>영속</b>한다(#193).
 *
 * <p><b>왜 캐시가 아니라 DB 인가.</b> 인메모리 캐시는 프로세스와 함께 죽어서, 배포할 때마다 관광빅데이터를 처음부터
 * 다시 긁었다. 배포가 하루 스무 번이면 그만큼 일일 한도(1,000건)를 태운다 — 넘는 순간 그날 남은 시간 동안
 * 추천·홈이 방문자 가중치 없이 돈다.
 *
 * <p>게다가 원본은 <b>완결된 달만 월 단위로</b> 발행된다. 6시간 TTL 로 하루 네 번 묻는 것은 같은 답을 반복해서
 * 확인하는 것이었다.
 */
@Entity
@Table(name = "region_visitor_aggregate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionVisitorAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 법정 시군구코드. 지명으로 집계하면 전국 동구 6곳이 한 버킷에 합산된다. */
    @Column(name = "signgu_code", nullable = false, length = 10)
    private String signguCode;

    /** 집계 기준 연월({@code YYYYMM}) — 어느 달 데이터인지 알아야 갱신이 필요한지 판단할 수 있다. */
    @Column(name = "base_ym", nullable = false, length = 6)
    private String baseYm;

    /** 관측 창 안의 관광객(외지인+외국인) 합. */
    @Column(name = "visitor_total", nullable = false)
    private double visitorTotal;

    /** 관측된 distinct 일자 수 — 베이지안 보정의 표본 크기다. */
    @Column(name = "observed_days", nullable = false)
    private int observedDays;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private RegionVisitorAggregate(
            String signguCode, YearMonth baseMonth, double visitorTotal, int observedDays, LocalDateTime updatedAt) {
        this.signguCode = requireCode(signguCode);
        this.baseYm = Objects.requireNonNull(baseMonth, "집계 기준 연월은 필수입니다").format(BASE_YM);
        if (visitorTotal < 0) {
            throw new IllegalArgumentException("방문자 합은 음수일 수 없습니다: " + visitorTotal);
        }
        if (observedDays < 0) {
            throw new IllegalArgumentException("관측 일수는 음수일 수 없습니다: " + observedDays);
        }
        this.visitorTotal = visitorTotal;
        this.observedDays = observedDays;
        this.updatedAt = Objects.requireNonNull(updatedAt, "갱신 시각은 필수입니다");
    }

    private static final java.time.format.DateTimeFormatter BASE_YM =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMM");

    public static RegionVisitorAggregate of(
            String signguCode, YearMonth baseMonth, double visitorTotal, int observedDays, LocalDateTime updatedAt) {
        return new RegionVisitorAggregate(signguCode, baseMonth, visitorTotal, observedDays, updatedAt);
    }

    /** 집계가 어느 달 것인지. 저장된 문자열을 도메인 타입으로 되돌린다. */
    public YearMonth baseMonth() {
        return YearMonth.parse(baseYm, BASE_YM);
    }

    private static String requireCode(String signguCode) {
        Objects.requireNonNull(signguCode, "법정 시군구코드는 필수입니다");
        if (signguCode.isBlank()) {
            throw new IllegalArgumentException("법정 시군구코드는 비어 있을 수 없습니다");
        }
        return signguCode;
    }
}
