package com.offway.core.trip.domain;

import com.offway.core.common.geo.Coordinate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전국문화축제표준데이터의 축제 한 건(#433) — 볼거리 후보.
 *
 * <h2>왜 두는가</h2>
 *
 * <p><b>89곳에 축제가 사실상 없다.</b> TourAPI {@code searchFestival2} 를 89곳 전수로 돌린 결과가
 * 합계 <b>1건</b>이었다(#392). 안동국제탈춤페스티벌이 있는 안동시가 0인데 같은 지역이 볼거리·맛집·숙박은
 * 임계를 넉넉히 넘긴다 — <b>지역이 얇은 게 아니라 축제 타입만 비어 있다.</b>
 *
 * <p>같은 89곳을 표준데이터로 재면 <b>446건</b>이고 88곳이 덮인다.
 *
 * <h2>기간을 스스로 든다</h2>
 *
 * <p>{@link FestivalPeriod} 는 TourAPI {@code contentId} 를 키로 <b>기간만</b> 따로 드는 표다(#388).
 * 그쪽이 나뉜 이유는 TourAPI 가 장소와 기간을 <b>다른 조회로</b> 주기 때문이다.
 *
 * <p>표준데이터는 <b>한 응답에 둘 다 온다.</b> 나눠 담으면 쓰기가 두 번이 되고, 더 나쁜 것은 재적재마다
 * 우리 {@code id} 가 바뀌면 그 키가 <b>다른 축제를 가리킨다</b>는 점이다. 그래서 기간을 여기서 든다.
 *
 * <p>판정 로직이 {@link FestivalPeriod#isOpenOn} 과 닮았지만 합치지 않는다 — 저쪽은 이미 배포된
 * 엔티티이고, 이 PR 에서 그 스키마·매핑까지 건드리면 범위가 는다.
 *
 * <h2>사진이 없다</h2>
 *
 * <p>TourAPI 와 달리 카드에 쓸 이미지가 안 온다. 이름·기간·좌표·설명은 오므로 코스에는 올릴 수 있지만,
 * <b>카드가 채워지는지</b>(#394 의 기준)에서는 이 점을 감안해야 한다.
 *
 * <p>지역은 다른 도메인(region)의 레퍼런스라 raw {@code regionId} 로만 참조한다(persistence-convention).
 */
@Entity
@Table(
        name = "festival_place",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_festival_place",
                        columnNames = {"region_id", "name", "event_start"}),
        indexes = {@Index(name = "idx_festival_place_region", columnList = "region_id, event_end")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FestivalPlace {

    /** 대한민국 육지·부속도서 좌표 범위. 지자체가 잘못 올린 행을 걸러내는 하한·상한이다. */
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 39.0;
    private static final double MIN_LNG = 124.0;
    private static final double MAX_LNG = 132.0;

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_VENUE_LENGTH = 300;
    private static final int MAX_ADDRESS_LENGTH = 300;
    private static final int MAX_HOST_LENGTH = 200;
    private static final int MAX_TEL_LENGTH = 50;
    private static final int MAX_URL_LENGTH = 500;

    /** 공개 식별자 접두어 — TourAPI contentId(숫자)·인허가(LIC-)·국가유산(HER-)과 갈라 준다. */
    private static final String ID_PREFIX = "FST-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 인구감소지역 89곳 중 하나(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    /** 개최장소 — "안동시 탈춤공원 일원" 처럼 주소보다 사람 말에 가깝다. 없을 수 있다. */
    @Column(length = MAX_VENUE_LENGTH)
    private String venue;

    @Column(nullable = false, length = MAX_ADDRESS_LENGTH)
    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "event_start", nullable = false)
    private LocalDate eventStart;

    @Column(name = "event_end", nullable = false)
    private LocalDate eventEnd;

    /** 축제내용. 지자체가 쓴 소개 글이라 길이도 문체도 제각각이다. 없을 수 있다. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 주관기관명. 없을 수 있다. */
    @Column(length = MAX_HOST_LENGTH)
    private String host;

    @Column(length = MAX_TEL_LENGTH)
    private String tel;

    @Column(name = "homepage_url", length = MAX_URL_LENGTH)
    private String homepageUrl;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    /**
     * <b>좌표는 필수다.</b> 표준데이터 446건 중 101건이 좌표가 없는데(신안군은 25건 중 24건),
     * 좌표 없는 장소는 동선에 못 올려 코스에 쓸 수가 없다. 여기서 막아 후보 풀에 들어오지 않게 한다.
     *
     * <b>기간도 필수다.</b> 시작이 종료보다 늦으면 {@link #isOpenOn} 이 어떤 날짜에도 참이 아니라,
     * 있는 축제를 우리가 지우는 셈이 된다.
     */
    @Builder
    private FestivalPlace(
            Long regionId,
            String name,
            String venue,
            String address,
            double lat,
            double lng,
            LocalDate eventStart,
            LocalDate eventEnd,
            String description,
            String host,
            String tel,
            String homepageUrl,
            LocalDateTime fetchedAt) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.name = requireText(name, MAX_NAME_LENGTH, "축제명");
        this.venue = trimToLength(venue, MAX_VENUE_LENGTH);
        this.address = requireText(address, MAX_ADDRESS_LENGTH, "소재지");
        requireInKorea(lat, lng);
        this.lat = lat;
        this.lng = lng;
        this.eventStart = Objects.requireNonNull(eventStart, "개최 시작일은 필수입니다");
        this.eventEnd = Objects.requireNonNull(eventEnd, "개최 종료일은 필수입니다");
        if (eventStart.isAfter(eventEnd)) {
            throw new IllegalArgumentException("개최 시작일이 종료일보다 늦습니다: " + eventStart + " ~ " + eventEnd);
        }
        this.description = trimToNull(description);
        this.host = trimToLength(host, MAX_HOST_LENGTH);
        this.tel = trimToLength(tel, MAX_TEL_LENGTH);
        this.homepageUrl = trimToLength(homepageUrl, MAX_URL_LENGTH);
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "조회 시각은 필수입니다");
    }

    /** 이 축제의 좌표. 거리 계산·클러스터링은 좌표 값객체가 소유한다. */
    public Coordinate coordinate() {
        return new Coordinate(lat, lng);
    }

    /** 여행일에 이 축제가 열려 있는가 — 시작일·종료일 당일을 포함한다. */
    public boolean isOpenOn(LocalDate date) {
        return !date.isBefore(eventStart) && !date.isAfter(eventEnd);
    }

    /**
     * 클라이언트에 나가는 식별자 — 다른 출처와 섞이지 않게 접두어를 붙인다.
     *
     * <p>코스 응답의 {@code poiContentId} 에는 여러 출처가 섞여 나간다. 접두어 하나로 상세 조회가 어느
     * 저장소를 봐야 하는지 갈린다.
     */
    public static String publicId(Long id) {
        return ID_PREFIX + Objects.requireNonNull(id, "축제 ID는 필수입니다");
    }

    /** 이 축제의 공개 식별자. */
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
            return Optional.empty(); // "FST-abc" 처럼 접두어만 흉내낸 값
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
     * 선택 값은 <b>길다고 버리지 않고 잘라 담는다.</b> 전화번호 하나가 길다고 축제를 통째로 버리면
     * 이름·기간·좌표까지 잃는데, 그쪽이 코스에 필요한 것이다.
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

    /**
     * 이 좌표를 담을 수 있는가 — <b>어댑터가 먼저 물어보는 자리</b>.
     *
     * <p>판정을 공개하는 이유는 {@code toPlace()} 에서 터지는 것을 막기 위해서다. 지자체가 좌표를
     * 0 으로 올린 행 하나가 예외를 던지면 <b>그달 적재가 통째로 실패한다</b> — 그 한 건만 건너뛰면
     * 될 일이다.
     *
     * <p>범위를 어댑터에 복사해 적지 않는다. 두 곳에 적으면 한쪽만 바뀐다.
     */
    public static boolean isInKorea(double lat, double lng) {
        return Double.isFinite(lat) && lat >= MIN_LAT && lat <= MAX_LAT
                && Double.isFinite(lng) && lng >= MIN_LNG && lng <= MAX_LNG;
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
