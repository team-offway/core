package com.offway.core.trip.domain;

import com.offway.core.transport.domain.Coordinate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지방행정인허가 데이터에서 온 장소 한 건(#144) — 숙박·맛집·볼거리 후보.
 *
 * <p><b>왜 두는가.</b> TourAPI 는 관광사업체로 등록된 곳 위주라 지방 숙소·식당이 통째로 빠진다. 의성군 숙박이 TourAPI 로는
 * 1건인데 인허가 데이터로는 52건이다. 89곳 중 31곳이 2박3일 숙박 2곳을 못 채우고, 부족하면 에러 없이 슬롯이 빠져
 * "잘 곳 없는 2박3일" 이 정상 응답으로 나갔다.
 *
 * <p><b>왜 DB 인가.</b> 분기 단위로만 바뀌는 레퍼런스 데이터를 매 요청 외부에서 끌어올 이유가 없다. 외부 한도가 소진되거나
 * 포털이 점검에 들어가도 코스는 나가야 한다.
 *
 * <p>지역은 다른 도메인(region)의 레퍼런스라 raw {@code regionId} 로만 참조한다(persistence-convention).
 */
@Entity
@Table(
        name = "licensed_place",
        indexes = {@Index(name = "idx_licensed_place_region_kind", columnList = "region_id, kind")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LicensedPlace {

    /** 대한민국 육지·부속도서 위도 범위. 좌표 변환(EPSG:5174 → WGS84)이 어긋난 행을 걸러내는 하한·상한이다. */
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 39.0;
    private static final double MIN_LNG = 124.0;
    private static final double MAX_LNG = 132.0;

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_ADDRESS_LENGTH = 300;
    private static final int MAX_TEL_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 인구감소지역 89곳 중 하나(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PlaceKind kind;

    @Column(nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private PlaceCategory category;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(length = MAX_ADDRESS_LENGTH)
    private String address;

    /** 인허가 데이터에서 29% 만 채워진다. 없는 게 정상이라 null 을 허용한다. */
    @Column(length = MAX_TEL_LENGTH)
    private String tel;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Builder
    private LicensedPlace(
            Long regionId,
            PlaceKind kind,
            PlaceCategory category,
            String name,
            String address,
            String tel,
            double lat,
            double lng) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.kind = Objects.requireNonNull(kind, "장소 종류는 필수입니다");
        this.category = Objects.requireNonNull(category, "장소 분류는 필수입니다");
        this.name = requireText(name, MAX_NAME_LENGTH, "상호");
        this.address = trimToNull(address, MAX_ADDRESS_LENGTH);
        this.tel = trimToNull(tel, MAX_TEL_LENGTH);
        requireInKorea(lat, lng);
        this.lat = lat;
        this.lng = lng;
    }

    /** 이 장소의 좌표. 거리 계산·클러스터링은 좌표 값객체가 소유한다. */
    public Coordinate coordinate() {
        return new Coordinate(lat, lng);
    }

    /** 코스에 우선 채울 장소인가 — 한옥·사찰처럼 그 자체가 관광 콘텐츠인 분류. */
    public boolean isPreferred() {
        return category.isPreferred();
    }

    private static String requireText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "는 비어 있을 수 없습니다");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + "가 너무 깁니다: " + trimmed.length());
        }
        return trimmed;
    }

    private static String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static void requireInKorea(double lat, double lng) {
        if (!Double.isFinite(lat) || lat < MIN_LAT || lat > MAX_LAT) {
            throw new IllegalArgumentException("위도가 대한민국 범위를 벗어났습니다: " + lat);
        }
        if (!Double.isFinite(lng) || lng < MIN_LNG || lng > MAX_LNG) {
            throw new IllegalArgumentException("경도가 대한민국 범위를 벗어났습니다: " + lng);
        }
    }
}
