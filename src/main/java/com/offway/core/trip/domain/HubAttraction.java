package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지자체의 중심 관광지 — 관광공사가 <b>실제 이동 데이터</b>로 매긴 순위(#185).
 *
 * <p><b>왜 이걸 쓰나.</b> "그 지역의 진짜 대표가 뭐냐" 를 우리가 점수식으로 만들 필요가 없다. 타 관광지와 가장 많이
 * 연결되는 곳을 관광공사가 이미 계산해 놨다. TourAPI 조회순보다 대표성이 높다 — 공주시에서 조회순 1위는
 * {@code 연미산 자연미술공원} 이지만 중심 1위는 <b>공산성</b> 이고, 구글이 "주요 명소" 로 보여주는 것도 그쪽이다.
 *
 * <p><b>1위가 곧 대표 사진감은 아니다.</b> 정선군 1위는 콘도, 2위는 카지노다 — 사람들이 실제로 그리로 가니 데이터는
 * 맞지만 지역 카드에 걸 그림은 아니다. 그래서 {@link #categoryLarge} 를 함께 들고 용도별로 거른다.
 */
@Entity
@Table(name = "hub_attraction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HubAttraction {

    /** 대표 사진·볼거리로 쓸 대분류. 숙박·음식은 지역을 대표하지 않는다. */
    public static final String CATEGORY_SIGHT = "관광지";

    private static final DateTimeFormatter BASE_YM = DateTimeFormatter.ofPattern("yyyyMM");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 우리 지역(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(name = "base_ym", nullable = false, length = 6)
    private String baseYm;

    /** 지자체 안 순위(1부터). 낮을수록 중심에 가깝다. */
    @Column(name = "hub_rank", nullable = false)
    private int hubRank;

    /** 데이터랩의 관광지 식별자 — 우리 POI 체계와 다르므로 그대로 보관한다. */
    @Column(name = "hub_code", nullable = false, length = 64)
    private String hubCode;

    @Column(nullable = false, length = 200)
    private String name;

    /** 대분류 — 관광지·음식·숙박. */
    @Column(name = "category_large", length = 50)
    private String categoryLarge;

    /** 중분류 — 역사관광·문화관광·자연관광·쇼핑 등. 화면 칩으로 쓴다. */
    @Column(name = "category_medium", length = 50)
    private String categoryMedium;

    private Double lat;

    private Double lng;

    @Builder
    private HubAttraction(
            Long regionId,
            YearMonth baseMonth,
            int hubRank,
            String hubCode,
            String name,
            String categoryLarge,
            String categoryMedium,
            Double lat,
            Double lng) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.baseYm = Objects.requireNonNull(baseMonth, "기준 연월은 필수입니다").format(BASE_YM);
        if (hubRank < 1) {
            throw new IllegalArgumentException("순위는 1 이상이어야 합니다: " + hubRank);
        }
        this.hubRank = hubRank;
        this.hubCode = requireText(hubCode, "관광지 식별자");
        this.name = requireText(name, "관광지명");
        this.categoryLarge = categoryLarge;
        this.categoryMedium = categoryMedium;
        this.lat = lat;
        this.lng = lng;
    }

    /**
     * 기준 연월의 <b>저장 표현</b>({@code yyyyMM}). 리포지토리 질의가 월을 문자열로 거를 때 쓴다.
     *
     * <p>고정폭이라 사전순 비교가 곧 시간순 비교다.
     */
    public static String toBaseYm(YearMonth month) {
        return month.format(BASE_YM);
    }

    public YearMonth baseMonth() {
        return YearMonth.parse(baseYm, BASE_YM);
    }

    /** 지역 대표 사진·볼거리로 쓸 수 있는가 — 숙박·음식은 제외한다. */
    public boolean isSight() {
        return CATEGORY_SIGHT.equals(categoryLarge);
    }

    /** 좌표가 있어야 슬롯에 넣거나 다른 소스와 이을 수 있다. */
    public boolean hasCoordinate() {
        return lat != null && lng != null;
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + "은(는) 필수입니다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + "은(는) 비어 있을 수 없습니다");
        }
        return value;
    }
}
