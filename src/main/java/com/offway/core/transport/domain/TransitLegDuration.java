package com.offway.core.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 버스·여객선 <b>구간 하나</b>의 소요시간(#107 · #97).
 *
 * <h2>왜 시간표가 아니라 소요시간인가</h2>
 *
 * <p>고속·시외버스는 오늘~+2일, 여객선은 오늘~+7일만 배차를 답한다(실측 2026-08-31). 연차를 기준으로 다음 달
 * 코스를 짜는 서비스라 <b>요청 시점에 시간표를 물을 수 없다.</b>
 *
 * <p>그런데 같은 구간의 소요시간은 편마다 같았다 — 동서울→정선 7편이 전부 150분·28,600원·우등이다. 그래서
 * 시간표는 버리고 소요시간만 남긴다.
 *
 * <h2>왜 미리 다 재지 않는가</h2>
 *
 * <p>터미널 789곳·항구 500곳이라 모든 짝은 수십만이다. {@code unroutable_probe} 와 같은 방식을 쓴다 —
 * <b>쓰다가 필요해진 짝만</b> 기억한다. 코스가 물었는데 값이 없으면 자리만 만들고({@code measuredAt} 이 비어
 * 있다) 배치가 나중에 채운다.
 *
 * <h2>"운행 없음" 도 결과다</h2>
 *
 * <p>{@code measuredAt} 은 있는데 {@code minutes} 가 없으면 "재봤더니 그 구간은 다니지 않는다" 는 뜻이다.
 * 이 행을 지우면 배치가 같은 구간을 영원히 다시 잰다.
 */
@Entity
@Table(name = "transit_leg_duration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransitLegDuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransitMode mode;

    @Column(name = "dep_code", nullable = false, length = 16)
    private String depCode;

    @Column(name = "arr_code", nullable = false, length = 16)
    private String arrCode;

    /** 소요시간(분). 미측정이거나 그 구간에 운행이 없으면 비어 있다. */
    @Column private Integer minutes;

    @Column private Integer charge;

    @Column(name = "vehicle_name", length = 64)
    private String vehicleName;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    private TransitLegDuration(TransitMode mode, String depCode, String arrCode, LocalDateTime requestedAt) {
        this.mode = Objects.requireNonNull(mode, "수단은 null 일 수 없습니다.");
        this.depCode = Objects.requireNonNull(depCode, "출발 코드는 null 일 수 없습니다.");
        this.arrCode = Objects.requireNonNull(arrCode, "도착 코드는 null 일 수 없습니다.");
        this.requestedAt = Objects.requireNonNull(requestedAt, "요청 시각은 null 일 수 없습니다.");
    }

    /** 코스가 물었는데 값이 없을 때 만드는 <b>빈 자리</b>. 배치가 나중에 채운다. */
    public static TransitLegDuration requested(
            TransitMode mode, String depCode, String arrCode, LocalDateTime now) {
        return new TransitLegDuration(mode, depCode, arrCode, now);
    }

    /** 실호출 결과를 적는다. {@code leg} 가 null 이면 "재봤더니 운행이 없다" 는 뜻이고, 그것도 결과다. */
    public void measured(MeasuredLeg leg, LocalDateTime now) {
        this.measuredAt = Objects.requireNonNull(now, "측정 시각은 null 일 수 없습니다.");
        if (leg == null) {
            this.minutes = null;
            this.charge = null;
            this.vehicleName = null;
            return;
        }
        this.minutes = leg.minutes();
        this.charge = leg.charge();
        this.vehicleName = leg.vehicleName();
    }

    /** 쓸 수 있는 소요시간 — 재지 않았거나 운행이 없으면 빈 값. */
    public Optional<Integer> usableMinutes() {
        return Optional.ofNullable(minutes);
    }

    /** 아직 안 쟀는가 — 배치가 고를 대상. */
    public boolean isUnmeasured() {
        return measuredAt == null;
    }
}
