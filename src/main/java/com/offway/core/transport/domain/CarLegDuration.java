package com.offway.core.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자차 구간 하나의 <b>실측 소요시간</b>(#584).
 *
 * <p>대중교통은 이미 {@code transit_leg_duration} 에 같은 것을 남겨 두고 쓴다(#107 · #469).
 * 자차만 캐시가 없어, 같은 코스를 다시 만들 때마다 TMAP 을 다시 불렀다.
 *
 * <p><b>키 공간이 유한하다.</b> 좌표가 우리 장소 풀에서 오므로 짝이 무한정 늘지 않는다 — 그래서
 * 상한 없이 표에 쌓아도 된다.
 *
 * <p><b>방향이 있다.</b> A→B 와 B→A 는 일방통행·고가 때문에 다를 수 있어 한 행으로 합치지 않는다.
 */
@Entity
@Table(
        name = "car_leg_duration",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_car_leg",
                        columnNames = {"from_lat", "from_lng", "to_lat", "to_lng"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarLegDuration {

    /**
     * 다시 재는 주기.
     *
     * <p>{@code transit_leg_duration} 과 같은 90일이다(#469). 도로는 느리게 변하지만 <b>안 변하지는
     * 않는다</b> — 새로 뚫린 길, 없어진 길이 반영되려면 언젠가 다시 물어야 한다. 한도가 1,000 이라
     * 이 주기를 짧게 둘 여유가 있지만, 같은 성격의 값에 다른 숫자를 두면 "왜 다른가" 를 매번 묻게 된다.
     */
    public static final Duration REMEASURE_AFTER = Duration.ofDays(90);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_lat", nullable = false, precision = 10, scale = CoordinateKey.SCALE)
    private BigDecimal fromLat;

    @Column(name = "from_lng", nullable = false, precision = 10, scale = CoordinateKey.SCALE)
    private BigDecimal fromLng;

    @Column(name = "to_lat", nullable = false, precision = 10, scale = CoordinateKey.SCALE)
    private BigDecimal toLat;

    @Column(name = "to_lng", nullable = false, precision = 10, scale = CoordinateKey.SCALE)
    private BigDecimal toLng;

    /** 실측 소요시간(분). <b>TMAP 이 실제로 답한 값만</b> 들어온다 — 폴백은 저장하지 않는다. */
    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    private CarLegDuration(
            CoordinateKey from, CoordinateKey to, int minutes, LocalDateTime measuredAt) {
        Objects.requireNonNull(from, "출발 좌표는 필수입니다");
        Objects.requireNonNull(to, "도착 좌표는 필수입니다");
        if (minutes <= 0) {
            // 0 이하는 TMAP 이 답할 수 없는 값이다. 그대로 저장하면 코스가 "0분 이동" 을 그린다.
            throw new IllegalArgumentException("소요시간은 1분 이상이어야 합니다: " + minutes);
        }
        this.fromLat = from.lat();
        this.fromLng = from.lng();
        this.toLat = to.lat();
        this.toLng = to.lng();
        this.minutes = minutes;
        this.measuredAt = Objects.requireNonNull(measuredAt, "측정 시각은 필수입니다");
    }

    /** 잰 값 하나 — 결과가 입력에서 도출되므로 빌더가 아니라 팩토리다(조립이면 빌더, 계산이면 팩토리). */
    public static CarLegDuration measured(
            CoordinateKey from, CoordinateKey to, int minutes, LocalDateTime measuredAt) {
        return new CarLegDuration(from, to, minutes, measuredAt);
    }

    /** 아직 쓸 수 있는 값인가 — 재측정 주기를 넘겼으면 다시 잰다. */
    public boolean isFresh(LocalDateTime now) {
        return measuredAt.plus(REMEASURE_AFTER).isAfter(now);
    }
}
