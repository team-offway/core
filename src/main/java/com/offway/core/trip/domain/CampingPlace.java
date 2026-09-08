package com.offway.core.trip.domain;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.common.geo.KoreaBounds;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고캠핑 야영장 한 건(#510) — <b>숙박 후보</b>.
 *
 * <h2>왜 두는가 — 수가 아니라 사진이다</h2>
 *
 * <p>숙박 후보 자체는 얇지 않다. 인허가 숙박({@link LicensedPlace})이 지역당 평균 296곳이다. 그런데
 * <b>그 표에는 사진 컬럼이 아예 없다</b> — 이름·주소·전화뿐이라 코스에 올라가도 화면이 회색 판이 된다.
 *
 * <p>사진이 있는 숙박은 TourAPI 쪽 922건뿐이고, 89곳으로 나누면 <b>지역당 열두 곳 남짓</b>이다. 그게
 * 실제로 화면에 쓸 수 있는 전부다.
 *
 * <p>고캠핑을 우리 89곳으로 재면 운영 중인 것이 1,702건이고 좌표 99.8%·사진 75% 가 온다. 겹치는
 * 375건(이름이 같거나 좌표 200m 이내)을 빼도 <b>순증 1,323건, 그중 사진이 964건</b>이다 — 사진 있는
 * 숙박 후보가 922 → 1,886건으로 <b>2.05배</b>가 된다(2026-09-08 운영 DB 대조).
 *
 * <h2>보충이 아니라 같은 급의 후보다</h2>
 *
 * <p>인허가·국가유산은 {@code RegionPois.needsMoreStays()} 가 참일 때만 쓰인다. 그 임계가 <b>2</b> 라,
 * 지역당 평균 12건인 우리 숙박 풀에서는 사실상 한 번도 참이 아니다 — 같은 자리에 넣으면 야영장이
 * 한 건도 안 쓰인다.
 *
 * <p>야영장은 사진 75%·좌표 99.7% 를 들고 오므로 사진 0% 인 인허가와 같은 취급을 할 이유가 없다.
 * 축제(#433)가 간 길대로 <b>보충 판정 밖에서</b> 숙박 풀에 병합한다.
 *
 * <h2>운영 정보가 함께 온다</h2>
 *
 * <p>우리 DB 출처 중 <b>상세가 비지 않는 유일한 장소</b>다. 인허가·국가유산·축제는 "언제 여나" 를
 * 몰라 지도 링크로 넘겼는데, 야영장은 운영기간(78%)·운영일(80%)·예약방법(63%)이 온다.
 *
 * <p>지역은 다른 도메인(region)의 레퍼런스라 raw {@code regionId} 로만 참조한다(persistence-convention).
 */
@Entity
@Table(
        name = "camping_place",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_camping_place_external", columnNames = "external_id"),
        indexes = {@Index(name = "idx_camping_place_region", columnList = "region_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampingPlace {

    private static final int MAX_EXTERNAL_ID_LENGTH = 50;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_ADDRESS_LENGTH = 300;
    private static final int MAX_INDUTY_LENGTH = 100;
    private static final int MAX_URL_LENGTH = 500;
    private static final int MAX_LINE_INTRO_LENGTH = 500;
    private static final int MAX_TEL_LENGTH = 50;
    private static final int MAX_OPERATION_LENGTH = 200;

    /** 공개 식별자 접두어 — TourAPI contentId(숫자)·인허가(LIC-)·국가유산(HER-)·축제(FST-)와 갈라 준다. */
    private static final String ID_PREFIX = "CMP-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 인구감소지역 89곳 중 하나(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /**
     * 고캠핑이 주는 야영장 식별자 — <b>자연키</b>.
     *
     * <p>우리 {@code id} 를 자연키로 쓸 수 없는 이유는 축제와 같다. 재적재마다 바뀌면 {@code CMP-{id}} 가
     * 다른 야영장을 가리켜, 코스에 실어 보낸 식별자가 엉뚱한 곳의 상세를 연다.
     *
     * <p>TourAPI {@code contentId} 와 <b>체계가 다르다</b> — 실측에서 두 집합의 교집합이 0이었다.
     * 그래서 겹침은 id 가 아니라 이름·좌표로 가른다({@code RegionPois.identity()}).
     */
    @Column(name = "external_id", nullable = false, length = MAX_EXTERNAL_ID_LENGTH)
    private String externalId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false, length = MAX_ADDRESS_LENGTH)
    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    /** 업종이 곧 뱃지다 — `일반야영장`·`자동차야영장`·`글램핑`·`카라반`. 둘 이상이면 쉼표로 이어 온다. */
    @Column(length = MAX_INDUTY_LENGTH)
    private String induty;

    /** 대표사진. <b>이 필드가 이 엔티티의 존재 이유다</b> — 74% 가 채워진다. */
    @Column(name = "image_url", length = MAX_URL_LENGTH)
    private String imageUrl;

    /** 한 줄 소개 — 카드 캐치프레이즈 자리에 그대로 쓸 수 있는 길이로 온다. */
    @Column(name = "line_intro", length = MAX_LINE_INTRO_LENGTH)
    private String lineIntro;

    /** 소개글. 길이가 제각각이라 TEXT 로 둔다. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String intro;

    @Column(length = MAX_TEL_LENGTH)
    private String tel;

    @Column(name = "homepage_url", length = MAX_URL_LENGTH)
    private String homepageUrl;

    /** 운영기간 — `봄,여름,가을`·`연중` 처럼 온다. */
    @Column(name = "oper_period", length = MAX_OPERATION_LENGTH)
    private String operPeriod;

    /** 운영일 — `평일+주말`·`주말` 처럼 온다. */
    @Column(name = "oper_days", length = MAX_OPERATION_LENGTH)
    private String operDays;

    /** 예약방법 — `전화`·`온라인` 처럼 온다. */
    @Column(length = MAX_OPERATION_LENGTH)
    private String reservation;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    /**
     * <b>좌표는 필수다.</b> 좌표 없는 장소는 동선에 못 올려 코스에 쓸 수가 없다. 어댑터가 먼저 거르지만
     * 여기서도 막아 후보 풀에 들어오지 않게 한다.
     *
     * <p><b>휴장 여부는 여기서 묻지 않는다.</b> 적재 단계가 운영 중인 것만 넘기므로, 이 표에 담긴 것은
     * 전부 운영 중이다 — 상태 필드를 두면 "담겼는데 안 쓰이는 행" 이 생기고 조회마다 그걸 걸러야 한다.
     */
    @Builder
    private CampingPlace(
            Long regionId,
            String externalId,
            String name,
            String address,
            double lat,
            double lng,
            String induty,
            String imageUrl,
            String lineIntro,
            String intro,
            String tel,
            String homepageUrl,
            String operPeriod,
            String operDays,
            String reservation,
            LocalDateTime fetchedAt) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.externalId = requireText(externalId, MAX_EXTERNAL_ID_LENGTH, "야영장 식별자");
        this.name = requireText(name, MAX_NAME_LENGTH, "야영장명");
        this.address = requireText(address, MAX_ADDRESS_LENGTH, "소재지");
        KoreaBounds.require(lat, lng);
        this.lat = lat;
        this.lng = lng;
        this.induty = trimToLength(induty, MAX_INDUTY_LENGTH);
        this.imageUrl = trimToLength(imageUrl, MAX_URL_LENGTH);
        this.lineIntro = trimToLength(lineIntro, MAX_LINE_INTRO_LENGTH);
        this.intro = trimToNull(intro);
        this.tel = trimToLength(tel, MAX_TEL_LENGTH);
        this.homepageUrl = trimToLength(homepageUrl, MAX_URL_LENGTH);
        this.operPeriod = trimToLength(operPeriod, MAX_OPERATION_LENGTH);
        this.operDays = trimToLength(operDays, MAX_OPERATION_LENGTH);
        this.reservation = trimToLength(reservation, MAX_OPERATION_LENGTH);
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "조회 시각은 필수입니다");
    }

    /** 이 야영장의 좌표. 거리 계산·클러스터링은 좌표 값객체가 소유한다. */
    public Coordinate coordinate() {
        return new Coordinate(lat, lng);
    }

    /**
     * 카드에 실을 사진이 있는가 — <b>후보를 고르는 순서가 이걸로 갈린다</b>.
     *
     * <p>사진 없는 야영장을 앞세우면 이 표를 들여온 이유가 사라진다. 그렇다고 버리지는 않는다 —
     * 이름·주소·좌표는 있어 동선에는 올릴 수 있고, 얇은 지역에서는 그것도 후보다.
     */
    public boolean hasPhoto() {
        return imageUrl != null && !imageUrl.isBlank();
    }

    /**
     * 운영 정보를 하나라도 아는가.
     *
     * <p>우리 DB 출처 중 이 값을 가진 것은 야영장뿐이다. 상세에서 "언제 여나" 를 채울지, 아니면 다른
     * 출처처럼 지도로 넘길지가 여기서 갈린다.
     */
    public boolean knowsOperation() {
        return operPeriod != null || operDays != null || reservation != null;
    }

    /**
     * 클라이언트에 나가는 식별자 — 다른 출처와 섞이지 않게 접두어를 붙인다.
     *
     * <p>코스 응답의 {@code poiContentId} 에는 여러 출처가 섞여 나간다. 접두어 하나로 상세 조회가 어느
     * 저장소를 봐야 하는지 갈린다.
     */
    public static String publicId(Long id) {
        return ID_PREFIX + Objects.requireNonNull(id, "야영장 ID는 필수입니다");
    }

    /** 이 야영장의 공개 식별자. */
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
            return Optional.empty(); // "CMP-abc" 처럼 접두어만 흉내낸 값
        }
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

    /**
     * 선택 값은 <b>길다고 버리지 않고 잘라 담는다.</b> 부대시설 안내 한 줄이 길다고 야영장을 통째로
     * 버리면 이름·좌표·사진까지 잃는데, 그쪽이 코스에 필요한 것이다.
     */
    private static String trimToLength(String value, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
