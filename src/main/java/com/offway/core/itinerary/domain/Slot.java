package com.offway.core.itinerary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 코스 타임라인의 한 칸 — 특정 시간대에 방문하는 장소 하나(관광/맛집/숙박)와 직전 장소로부터의 이동시간. 지도 핀을 위해 좌표를 들고 있다.
 * POI 는 다른 도메인(trip)의 레퍼런스라 애그리거트 경계를 넘으므로 raw content id 로만 참조한다(persistence-convention).
 *
 * <p>혜택 뱃지·비용은 정책 매칭(policy) 결과라 응답 시점에 붙인다 — 슬롯 상태로 두면 저장 코스가 정책 변경에 뒤처진다.
 */
@Entity
@Table(name = "slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 하루 안의 방문 순서(1부터). */
    @Column(name = "order_in_day", nullable = false)
    private int orderInDay;

    @Column(name = "time_of_day", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TimeOfDay timeOfDay;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SlotKind kind;

    /** TourAPI 콘텐츠 ID(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "poi_content_id", nullable = false, length = 64)
    private String poiContentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    /** 직전 슬롯에서 여기까지 이동시간(분). 하루 첫 슬롯은 0. */
    @Column(name = "travel_minutes_from_prev", nullable = false)
    private int travelMinutesFromPrev;

    private Slot(int orderInDay, TimeOfDay timeOfDay, SlotKind kind, String poiContentId, String title,
            double lat, double lng, int travelMinutesFromPrev) {
        if (orderInDay < 1) {
            throw new IllegalArgumentException("슬롯 순서는 1 이상이어야 합니다: " + orderInDay);
        }
        if (travelMinutesFromPrev < 0) {
            throw new IllegalArgumentException("이동시간은 음수일 수 없습니다: " + travelMinutesFromPrev);
        }
        requireCoordinate(lat, -90, 90, "위도");
        requireCoordinate(lng, -180, 180, "경도");
        this.orderInDay = orderInDay;
        this.timeOfDay = Objects.requireNonNull(timeOfDay, "시간대는 필수입니다");
        this.kind = Objects.requireNonNull(kind, "슬롯 종류는 필수입니다");
        this.poiContentId = requireText(poiContentId, "POI content id");
        this.title = requireText(title, "장소명");
        this.lat = lat;
        this.lng = lng;
        this.travelMinutesFromPrev = travelMinutesFromPrev;
    }

    /** 방문 슬롯을 만든다. 좌표·순서·이동시간 불변식을 스스로 검증한다. */
    public static Slot of(int orderInDay, TimeOfDay timeOfDay, SlotKind kind, String poiContentId, String title,
            double lat, double lng, int travelMinutesFromPrev) {
        return new Slot(orderInDay, timeOfDay, kind, poiContentId, title, lat, lng, travelMinutesFromPrev);
    }

    private static void requireCoordinate(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + "가 범위를 벗어났습니다: " + value);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 비어 있을 수 없습니다");
        }
        return value;
    }
}
