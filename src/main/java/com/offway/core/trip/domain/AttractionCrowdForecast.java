package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 관광지의 <b>하루치</b> 집중률 예측(#565).
 *
 * <h2>왜 이름이 자연키인가</h2>
 *
 * <p>이 API 는 콘텐츠 ID 를 주지 않는다. 관광지명({@code tAtsNm})뿐이라 우리 {@link RegionPoi#getTitle()}
 * 과 이름으로 맞춘다. 실측(2026-09-14, 표본 6곳)에서 정규화 없는 정확 일치가 느슨 일치와 같았다
 * (13/13 · 25/25 · 18/18) — 같은 공사 데이터라 표기가 어긋나지 않는다.
 *
 * <p>그래서 자연키가 <b>지역 + 관광지명 + 날짜</b>다. 이름은 지역 안에서만 유일하면 되고, 전국으로
 * 보면 "해수욕장" 처럼 겹치는 이름이 많아 지역을 반드시 낀다.
 *
 * <h2>값을 그대로 든다</h2>
 *
 * <p>0~100 이고 <b>관광지별 정규화가 아니다</b> — 실측에서 관광지별 30일 평균이 3.0~88.9 로 흩어졌다.
 * 늘 붐비는 곳과 늘 한산한 곳이 실제로 갈린다는 뜻이라, 관광지 간 비교가 되는 값으로 읽는다.
 * 문턱 판정은 {@link CrowdChip} 이 소유한다 — 엔티티는 잰 값만 든다.
 *
 * <p>지역은 다른 도메인(region)의 레퍼런스라 raw {@code regionId} 로만 참조한다(persistence-convention).
 */
@Entity
@Table(
        name = "attraction_crowd_forecast",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_attraction_crowd_natural",
                        columnNames = {"region_id", "attraction_name", "base_date"}),
        indexes = {
            @Index(name = "idx_attraction_crowd_lookup", columnList = "region_id, base_date"),
            @Index(name = "idx_attraction_crowd_fetched", columnList = "fetched_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttractionCrowdForecast {

    /** 집중률이 가질 수 있는 범위 — 밖이면 우리가 읽은 필드가 그 값이 아니라는 뜻이다. */
    private static final double MIN_RATE = 0.0;
    private static final double MAX_RATE = 100.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /** 관광지명 — 우리 {@code region_poi.title} 과 맞추는 키다. */
    @Column(name = "attraction_name", nullable = false, length = 200)
    private String attractionName;

    /** 예측 날짜. 향후 30일이 회차마다 새로 온다. */
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    /** 집중률 0~100. */
    @Column(nullable = false)
    private double rate;

    /** 이번 회차에 받은 시각 — 안 온 예보를 지우는 기준이다. */
    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Builder
    private AttractionCrowdForecast(Long regionId, String attractionName, LocalDate baseDate,
            double rate, LocalDateTime fetchedAt) {
        this.regionId = Objects.requireNonNull(regionId, "지역 id 는 필수입니다.");
        this.attractionName = requireText(attractionName, "관광지명은 필수입니다.");
        this.baseDate = Objects.requireNonNull(baseDate, "예측 날짜는 필수입니다.");
        this.rate = requireRate(rate);
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "수집 시각은 필수입니다.");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /**
     * 0~100 인지 본다 — <b>유한값 검사를 먼저 한다</b>.
     *
     * <p>{@code NaN} 은 어떤 비교에도 거짓이라 범위 검사만으로는 빠져나간다. 외부가 {@code "NaN"} 을
     * 보내면 {@code Double.valueOf} 가 그대로 파싱하고, 그 값이 DB 까지 간다 — MySQL 이 {@code DOUBLE}
     * 에 {@code NaN} 을 보존한다는 보장이 없어 저장 시점에 바뀌거나 거절된다. 저장 결과에 기대지 말고
     * 여기서 막는다.
     */
    private static double requireRate(double value) {
        if (!Double.isFinite(value) || value < MIN_RATE || value > MAX_RATE) {
            // 범위 밖이면 필드를 잘못 읽은 것이다 — 그대로 저장하면 문턱 판정이 조용히 틀린다.
            throw new IllegalArgumentException(
                    "집중률은 %.0f~%.0f 사이의 유한값이어야 합니다: %s".formatted(MIN_RATE, MAX_RATE, value));
        }
        return value;
    }
}
