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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 국가유산청에 등재된 국가유산 한 건(#160) — 볼거리 후보.
 *
 * <p><b>왜 두는가.</b> 인허가 데이터로 숙소·맛집은 넉넉해졌지만 볼거리는 여전히 얇고, 그마저 야영장·골프장이
 * 섞여 있다. 국가유산은 그 자체가 관광 자원이고 89곳 중 한 곳도 0건이 아니다. 우리 지역에서만 6,387건이 나오고
 * 그중 경북 1,489·경남 1,269·전남 992 로, <b>볼거리가 가장 얇던 쪽이 가장 두껍게</b> 채워진다.
 *
 * <p><b>인허가 장소와 다른 점은 사진과 설명이 온다는 것이다.</b> 인허가 데이터에는 둘 다 없어 카드가 비었다.
 * 국가유산은 수집분 기준 이미지 96%·설명 98%, 실제 적재분 기준 98%·99% 로 채워져 상세 화면이 그대로 산다.
 *
 * <p><b>data.go.kr 이 아니다.</b> 국가유산청 자체 API 라 관광 API 일일 한도와 무관하다. 한도가 소진돼
 * TourAPI 가 통째로 비는 날에도 이 후보는 남는다.
 *
 * <p>지역은 다른 도메인(region)의 레퍼런스라 raw {@code regionId} 로만 참조한다(persistence-convention).
 */
@Entity
@Table(
        name = "heritage_place",
        indexes = {@Index(name = "idx_heritage_place_region", columnList = "region_id, group_code")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HeritagePlace {

    /** 대한민국 육지·부속도서 좌표 범위. 지오코딩이 엉뚱하게 찍은 행을 걸러내는 하한·상한이다. */
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 39.0;
    private static final double MIN_LNG = 124.0;
    private static final double MAX_LNG = 132.0;

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_ADDRESS_LENGTH = 300;
    private static final int MAX_KIND_LENGTH = 40;
    private static final int MAX_GROUP_CODE_LENGTH = 40;
    private static final int MAX_IMAGE_URL_LENGTH = 500;

    /** 공개 식별자 접두어 — TourAPI contentId(숫자 문자열)·인허가(LIC-)와 갈라 준다. */
    private static final String ID_PREFIX = "HER-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 인구감소지역 89곳 중 하나(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /**
     * 종목 — 국보·보물·사적·시도유형문화유산 등. 화면에 뱃지로 그대로 나갈 문자열이다.
     *
     * <p>enum 으로 닫지 않는다. 제공기관이 종목 체계를 개편한 전례가 있고(문화재 → 국가유산), 우리는 이 값으로
     * 분기하지 않는다 — 분기는 {@link HeritageGroup} 이 소유한다.
     */
    @Column(nullable = false, length = MAX_KIND_LENGTH)
    private String kind;

    /** 대분류. 코스에 쓸 수 있는지를 이 값이 정한다. */
    @Column(name = "group_code", nullable = false, length = MAX_GROUP_CODE_LENGTH)
    @Enumerated(EnumType.STRING)
    private HeritageGroup group;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false, length = MAX_ADDRESS_LENGTH)
    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    /**
     * 대표 이미지. 적재분의 98% 가 채워지지만 없는 것이 있어 null 을 허용한다.
     *
     * <p><b>https 로 바꿔 담는다.</b> 원본이 주는 URL 은 http 이고 그대로 부르면 302 로 튕긴다(실측:
     * http 302 text/html 140B, https 200 image/jpg 69,774B). 앱은 https 로 서비스하므로 그대로 저장하면
     * 한 장도 안 뜬다. 규칙을 도메인이 소유해 적재 경로마다 다시 짜지 않게 한다.
     */
    @Column(name = "image_url", length = MAX_IMAGE_URL_LENGTH)
    private String imageUrl;

    /** 국가유산청이 주는 소개 글. 적재분의 99% 가 채워진다 — 인허가 장소에는 없던 것이다. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder
    private HeritagePlace(
            Long regionId,
            String kind,
            HeritageGroup group,
            String name,
            String address,
            double lat,
            double lng,
            String imageUrl,
            String description) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.group = Objects.requireNonNull(group, "국가유산 대분류는 필수입니다");
        this.kind = requireText(kind, MAX_KIND_LENGTH, "종목");
        this.name = requireText(name, MAX_NAME_LENGTH, "국가유산 이름");
        this.address = requireText(address, MAX_ADDRESS_LENGTH, "소재지");
        requireInKorea(lat, lng);
        this.lat = lat;
        this.lng = lng;
        this.imageUrl = secureImageUrl(imageUrl);
        this.description = trimToNull(description);
    }

    /** 이 국가유산의 좌표. 거리 계산·클러스터링은 좌표 값객체가 소유한다. */
    public Coordinate coordinate() {
        return new Coordinate(lat, lng);
    }

    /** 코스 스팟으로 쓸 수 있는가. */
    public boolean isVisitable() {
        return group.isVisitable();
    }

    /**
     * 클라이언트에 나가는 식별자 — TourAPI contentId·인허가 식별자와 섞이지 않게 접두어를 붙인다.
     *
     * <p>코스 응답의 {@code poiContentId} 에는 여러 출처가 섞여 나간다. 접두어 하나로 상세 조회가 어느 저장소를
     * 봐야 하는지 갈린다.
     */
    public static String publicId(Long id) {
        return ID_PREFIX + Objects.requireNonNull(id, "국가유산 ID는 필수입니다");
    }

    /** 이 국가유산의 공개 식별자. */
    public String publicId() {
        return publicId(id);
    }

    /**
     * 공개 식별자를 내부 ID 로 되돌린다.
     *
     * @return 우리 식별자면 내부 ID, 아니면 비어 있음
     */
    public static Optional<Long> parsePublicId(String publicId) {
        if (publicId == null || !publicId.startsWith(ID_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(publicId.substring(ID_PREFIX.length())));
        } catch (NumberFormatException e) {
            return Optional.empty(); // "HER-abc" 처럼 접두어만 흉내낸 값
        }
    }

    /** http 이미지를 https 로 올린다. 원본이 http 로만 주고, http 는 302 로 튕긴다. */
    private static String secureImageUrl(String url) {
        String trimmed = trimToNull(url);
        if (trimmed == null) {
            return null;
        }
        String secured = trimmed.startsWith("http://") ? "https://" + trimmed.substring("http://".length()) : trimmed;
        // 사진 하나 때문에 국가유산을 통째로 버리지 않는다 — 이름·좌표·설명은 멀쩡하다.
        return secured.length() > MAX_IMAGE_URL_LENGTH ? null : secured;
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

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
