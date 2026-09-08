package com.offway.core.trip.domain;

import com.offway.core.common.geo.Coordinate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 중심 관광지와 <b>실제로 함께 가는 곳</b>(#186).
 *
 * <h2>좌표 군집과 무엇이 다른가</h2>
 *
 * <p>지금 코스는 가까운 것끼리 묶는다({@code GeoCluster.selectCompact}). 동선은 짧아지지만 <b>"왜 이
 * 조합인가" 에는 답이 없다</b> — 옆에 있다는 것 말고는 이유가 없다.
 *
 * <p>이 값은 실제 방문 데이터에서 온다. "갑사에 간 사람들이 실제로 들르는 곳" 이라, 갑사 다음에
 * 신원사·동학사가 오고 끼니 자리에 동해원이 온다.
 *
 * <h2>좌표를 원본이 주지 않는다</h2>
 *
 * <p>{@code TarRlteTarService1} 응답에 {@code mapX}·{@code mapY} 가 <b>없다</b>. 그래서 인허가
 * 장소(#144)와 이름으로 이어 좌표를 얻고({@link PlaceNameKey}), <b>못 얻으면 담지 않는다</b> —
 * 좌표 없는 후보를 슬롯에 넣으면 동선이 깨진다.
 *
 * <p>실측(공주시): 연관 음식 75곳 중 <b>54곳(72%)</b> 이 인허가와 매칭됐다. 2박3일 식사 슬롯이
 * 여섯이라 그 정도면 넉넉하다.
 *
 * <h2>지역 밖은 애초에 안 들어온다</h2>
 *
 * <p>원본은 인접 시군 것을 섞어 준다 — 공주시 300건 중 45건(15%)이 천안·아산·부여였다. 그 필터는
 * 어댑터가 강제한다(호출자가 잊을 수 있는 자리에 두지 않는다).
 */
@Entity
@Table(
        name = "related_attraction",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_related_attraction",
                        columnNames = {"region_id", "base_ym", "hub_code", "related_code"}),
        indexes = {
            @Index(
                    name = "idx_related_attraction_hub",
                    columnList = "region_id, hub_code, category_large, related_rank")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelatedAttraction {

    private static final DateTimeFormatter BASE_YM = DateTimeFormatter.ofPattern("yyyyMM");

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_CATEGORY_LENGTH = 50;

    /** 대한민국 육지·부속도서 좌표 범위 — 조인이 엉뚱한 곳을 물어 오는 것을 막는 하한·상한이다. */
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 39.0;
    private static final double MIN_LNG = 124.0;
    private static final double MAX_LNG = 132.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 인구감소지역 89곳 중 하나(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /** 원본 기준월. 월 단위로 발행되므로 어느 달 것인지 함께 든다. */
    @Column(name = "base_ym", nullable = false, length = 6)
    private String baseYm;

    /** 중심 관광지 코드 — 이 줄이 "무엇과 함께 가는가" 의 그 무엇이다. */
    @Column(name = "hub_code", nullable = false, length = MAX_CODE_LENGTH)
    private String hubCode;

    @Column(name = "hub_name", nullable = false, length = MAX_NAME_LENGTH)
    private String hubName;

    /** 연관 관광지 코드. */
    @Column(name = "related_code", nullable = false, length = MAX_CODE_LENGTH)
    private String relatedCode;

    @Column(name = "related_name", nullable = false, length = MAX_NAME_LENGTH)
    private String relatedName;

    /** 중심 관광지 안에서의 순위(1부터). 낮을수록 함께 가는 정도가 크다. */
    @Column(name = "related_rank", nullable = false)
    private int relatedRank;

    /** 대분류 — 관광지·음식·숙박. 어느 슬롯에 넣을지를 이 값이 정한다. */
    @Column(name = "category_large", nullable = false, length = MAX_CATEGORY_LENGTH)
    private String categoryLarge;

    @Column(name = "category_medium", length = MAX_CATEGORY_LENGTH)
    private String categoryMedium;

    /**
     * 이름으로 이어 붙인 인허가 장소.
     *
     * <p><b>좌표만 베끼지 않고 id 를 든다.</b> 코스는 이 장소를 후보로 올려야 하는데, 후보 식별자는
     * {@code LIC-} 접두어를 단 인허가 id 다. 좌표만 있으면 같은 곳을 다시 찾아야 하고 그 조회가
     * 요청 경로에 생긴다.
     */
    @Column(name = "licensed_place_id", nullable = false)
    private Long licensedPlaceId;

    /**
     * 인허가 조인으로 얻은 좌표.
     *
     * <p><b>필수다.</b> 원본이 좌표를 안 줘서 못 얻는 행이 생기는데, 그건 저장하지 않는다 — 좌표 없는
     * 후보가 표에 있으면 조회하는 쪽이 매번 걸러야 하고 한 군데만 빠뜨려도 동선이 깨진다.
     */
    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Builder
    private RelatedAttraction(
            Long regionId,
            YearMonth baseMonth,
            String hubCode,
            String hubName,
            String relatedCode,
            String relatedName,
            int relatedRank,
            String categoryLarge,
            String categoryMedium,
            Long licensedPlaceId,
            double lat,
            double lng) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.baseYm = Objects.requireNonNull(baseMonth, "기준월은 필수입니다").format(BASE_YM);
        this.hubCode = requireText(hubCode, MAX_CODE_LENGTH, "중심 관광지 코드");
        this.hubName = requireText(hubName, MAX_NAME_LENGTH, "중심 관광지명");
        this.relatedCode = requireText(relatedCode, MAX_CODE_LENGTH, "연관 관광지 코드");
        this.relatedName = requireText(relatedName, MAX_NAME_LENGTH, "연관 관광지명");
        if (relatedRank < 1) {
            throw new IllegalArgumentException("연관 순위는 1 이상이어야 합니다: " + relatedRank);
        }
        this.relatedRank = relatedRank;
        this.categoryLarge = requireText(categoryLarge, MAX_CATEGORY_LENGTH, "대분류");
        this.categoryMedium = trimToLength(categoryMedium, MAX_CATEGORY_LENGTH);
        this.licensedPlaceId = Objects.requireNonNull(licensedPlaceId, "이어 붙인 인허가 장소는 필수입니다");
        requireInKorea(lat, lng);
        this.lat = lat;
        this.lng = lng;
    }

    public YearMonth baseMonth() {
        return YearMonth.parse(baseYm, BASE_YM);
    }

    public Coordinate coordinate() {
        return new Coordinate(lat, lng);
    }

    /** 이름을 맞대 볼 열쇠 — 인허가와 이을 때 쓴 그 규칙이다. */
    public Optional<PlaceNameKey> nameKey() {
        return PlaceNameKey.of(relatedName);
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

    private static String trimToLength(String value, int maxLength) {
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
