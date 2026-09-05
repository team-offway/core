package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지역 하루치 방문자(#394) — <b>혼잡도의 재료</b>.
 *
 * <h2>왜 일별로 두나</h2>
 *
 * <p>{@link RegionVisitorAggregate} 는 한 달을 한 줄로 뭉갠다. 그 형태로는 <b>"내가 가는 토요일에
 * 붐비나"</b> 에 답할 수 없다 — 요일도 계절도 그 압축에서 사라진다.
 *
 * <p>원본({@code locgoRegnVisitrDDList})은 애초에 일별·유형별로 준다. 우리가 버리고 있었을 뿐이다.
 *
 * <h2>랭킹과 갈라 둔다</h2>
 *
 * <p>집계 표의 {@code observedDays} 가 랭킹의 베이지안 prior 에 들어간다. 그 표를 건드리면 혼잡도를
 * 고치려다 추천 순서가 조용히 바뀐다. 두 관심사는 각자의 표를 본다.
 */
@Entity
@Table(name = "region_visitor_daily")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionVisitorDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 법정 시군구코드 — 지명이 아니다. 동구 6곳·중구 6곳처럼 같은 이름이 여럿이라 코드로만 맞춘다. */
    @Column(name = "signgu_code", nullable = false, length = 10)
    private String signguCode;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "visitor_type", nullable = false, length = 20)
    private VisitorType visitorType;

    @Column(name = "visitor_count", nullable = false)
    private double visitorCount;

    @Builder
    private RegionVisitorDaily(String signguCode, LocalDate baseDate, VisitorType visitorType, double visitorCount) {
        this.signguCode = requireText(signguCode);
        this.baseDate = Objects.requireNonNull(baseDate, "기준일자는 null 일 수 없습니다.");
        this.visitorType = Objects.requireNonNull(visitorType, "방문자 구분은 null 일 수 없습니다.");
        if (visitorCount < 0) {
            // 음수 방문자는 원본이 이상하다는 뜻이다. 그대로 쌓으면 평균이 조용히 내려가 한산해 보인다.
            throw new IllegalArgumentException("방문자수는 음수일 수 없습니다: " + visitorCount);
        }
        this.visitorCount = visitorCount;
    }

    /** 요일 — 파생이라 저장하지 않는다. 저장하면 날짜와 어긋날 자리가 하나 는다. */
    public DayOfWeek dayOfWeek() {
        return baseDate.getDayOfWeek();
    }

    /** 관광객으로 셀 것인가 — 거주자는 뺀다(방문자 ≠ 관광객). */
    public boolean isTourist() {
        return visitorType.isTourist();
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "시군구코드는 null 일 수 없습니다.");
        if (value.isBlank()) {
            throw new IllegalArgumentException("시군구코드는 비어 있을 수 없습니다.");
        }
        return value.strip();
    }
}
