package com.offway.core.transport.domain;

import com.offway.core.common.geo.Coordinate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좌표 목록 하나의 <b>최적 방문 순서</b>(#584) — TMAP 경유지최적화 결과.
 *
 * <h2>이 표가 지키는 것</h2>
 *
 * <p>경유지 최적화는 <b>일일 한도가 50</b> 으로 우리가 가진 것 중 가장 빡빡하다. 자차 코스 하나가
 * 날짜 수만큼 부르므로 2박3일이면 <b>17번 만에 마른다</b>. 마르면 직선거리 정렬로 폴백하는데 응답은
 * 200 이라, 순서가 틀린 줄 사용자가 알 방법이 없다.
 *
 * <h2>키가 순서를 보존한다</h2>
 *
 * <p>TMAP 은 <b>첫 점을 출발, 마지막 점을 도착으로 고정</b>하고 가운데만 최적화한다. 같은 집합이라도
 * 첫·끝이 다르면 결과가 다르므로, 키는 넘긴 순서 그대로여야 한다.
 *
 * <p>좌표는 {@link CoordinateKey} 로 정규화해 잇는다 — {@code double} 을 그대로 쓰면 같은 장소를 두
 * 경로로 읽어 마지막 자리가 한 번만 달라져도 다른 키가 되어, 캐시가 조용히 아무것도 못 찾는다.
 */
@Entity
@Table(
        name = "car_route_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_car_route_order_points", columnNames = "points"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarRouteOrder {

    /**
     * 다시 재는 주기 — 30일.
     *
     * <p>구간 소요시간(90일)보다 짧다. 요청에 {@code startTime=now} 가 실리고 {@code searchOption=0}
     * (교통최적)이라 <b>시간대에 따라 순서가 달라질 수 있기</b> 때문이다.
     *
     * <p>그래도 짧게 잡지 않는다. 볼거리 순서는 거리가 지배적이라 그 차이가 클 것 같지 않고, 무엇보다
     * <b>한도가 말라 직선거리로 떨어지는 것이 한 달 전 순서보다 훨씬 나쁘다.</b> 실측하면 조정한다.
     */
    public static final Duration REMEASURE_AFTER = Duration.ofDays(30);

    /** 키 칸 길이. 점 3~12개 × 좌표 한 쌍(약 22자)이라 280자를 안 넘는다. */
    public static final int MAX_POINTS_LENGTH = 512;

    /** 좌표 하나 안에서 위도·경도를 잇는다. */
    private static final String AXIS = ",";

    /** 좌표끼리 잇는다. */
    private static final String POINT = ";";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code "lat,lng;lat,lng;..."} — 넘긴 순서 그대로. */
    @Column(name = "points", nullable = false, length = MAX_POINTS_LENGTH)
    private String points;

    /** 최적 순서를 원본 인덱스로 — {@code "0,3,1,2"}. */
    @Column(name = "ordinals", nullable = false, length = 64)
    private String ordinals;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    private CarRouteOrder(String points, String ordinals, LocalDateTime measuredAt) {
        this.points = Objects.requireNonNull(points, "좌표 목록은 필수입니다");
        this.ordinals = Objects.requireNonNull(ordinals, "순서는 필수입니다");
        this.measuredAt = Objects.requireNonNull(measuredAt, "측정 시각은 필수입니다");
    }

    /** 잰 값 하나 — 계산이라 팩토리다. */
    public static CarRouteOrder measured(
            List<Coordinate> points, List<Integer> order, LocalDateTime measuredAt) {
        return new CarRouteOrder(keyOf(points), join(order), measuredAt);
    }

    /**
     * 좌표 목록을 키 문자열로.
     *
     * <p>{@code CoordinateKey} 로 자릿수를 고정한다 — 저장할 때와 찾을 때가 같은 규격이어야 한다.
     */
    public static String keyOf(List<Coordinate> points) {
        return points.stream()
                .map(CoordinateKey::of)
                .map(key -> key.lat().toPlainString() + AXIS + key.lng().toPlainString())
                .collect(Collectors.joining(POINT));
    }

    /** 이 키를 담을 수 있나 — 담을 수 없으면 캐시를 건너뛴다(없는 것처럼 군다). */
    public static boolean storable(String key) {
        return key.length() <= MAX_POINTS_LENGTH;
    }

    /** 저장된 순서를 인덱스 목록으로 되돌린다. */
    public List<Integer> order() {
        return java.util.Arrays.stream(ordinals.split(AXIS)).map(Integer::valueOf).toList();
    }

    /** 아직 쓸 수 있는 값인가. */
    public boolean isFresh(LocalDateTime now) {
        return measuredAt.plus(REMEASURE_AFTER).isAfter(now);
    }

    private static String join(List<Integer> order) {
        return order.stream().map(String::valueOf).collect(Collectors.joining(AXIS));
    }
}
