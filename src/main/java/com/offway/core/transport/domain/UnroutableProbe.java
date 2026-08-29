package com.offway.core.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * TMAP 이 좌표 탓으로 거절한 <b>구간 한 건</b>의 기록(#335).
 *
 * <h2>왜 "구간" 인가 — 어느 쪽이 나쁜지 한 번으로는 모른다</h2>
 *
 * <p>{@code A→B} 가 거절돼도 A 가 문제인지 B 가 문제인지 응답만으로는 못 가른다. TMAP 이 한국어 메시지에
 * "출발지" 라고 적어 주긴 하지만, 도착지가 문제일 때의 문구는 확인하지 못했다. <b>확인 못 한 문자열에
 * 판정을 얹지 않는다</b> — 틀리면 멀쩡한 장소가 조용히 코스에서 사라진다.
 *
 * <p>대신 <b>짝을 함께 적는다.</b> 진짜 못 푸는 좌표는 들어오는 구간과 나가는 구간이 <b>둘 다</b> 실패하므로
 * 서로 다른 짝이 둘 이상 쌓인다. 그 옆에 있었을 뿐인 멀쩡한 좌표는 짝이 하나뿐이다. 그래서 판정은
 * "짝이 둘 이상" 한 줄로 끝나고, 메시지를 읽을 필요가 없다.
 *
 * <p>같은 구간이 여러 번 실패해도 한 줄이다(UNIQUE). 같은 짝으로 두 번 실패한 것은 새 증거가 아니다 —
 * 그걸 세면 하루 종일 한 코스만 다시 만들어도 차단 조건이 채워진다.
 */
@Entity
@Table(name = "unroutable_probe")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnroutableProbe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 의심 좌표. */
    @Column(nullable = false, precision = 10, scale = CoordinateKey.SCALE)
    private BigDecimal lat;

    @Column(nullable = false, precision = 10, scale = CoordinateKey.SCALE)
    private BigDecimal lng;

    /** 같은 구간의 반대편 좌표 — 이 값이 몇 가지인지가 곧 증거의 수다. */
    @Column(name = "partner_lat", nullable = false, precision = 10, scale = CoordinateKey.SCALE)
    private BigDecimal partnerLat;

    @Column(name = "partner_lng", nullable = false, precision = 10, scale = CoordinateKey.SCALE)
    private BigDecimal partnerLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnroutableReason reason;

    @Builder
    private UnroutableProbe(CoordinateKey point, CoordinateKey partner, UnroutableReason reason) {
        Objects.requireNonNull(point, "point 는 필수입니다");
        Objects.requireNonNull(partner, "partner 는 필수입니다");
        this.reason = Objects.requireNonNull(reason, "reason 은 필수입니다");
        this.lat = point.lat();
        this.lng = point.lng();
        this.partnerLat = partner.lat();
        this.partnerLng = partner.lng();
    }

    public CoordinateKey point() {
        return new CoordinateKey(lat, lng);
    }

    public CoordinateKey partner() {
        return new CoordinateKey(partnerLat, partnerLng);
    }
}
