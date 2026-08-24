package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지역에 속한 장소 하나(#304) — <b>사진·소개를 갖춘 장소를 지역 단위로 꺼내기 위해</b> 저장한다.
 *
 * <p><b>왜 저장하나.</b> 같은 데이터가 코스 생성 응답에는 이미 실린다. 없던 것은 지역 단위로 그것을 받을
 * 입구였고, 요청 경로에서 TourAPI 를 부르면 지역당 3콜이라 사용자 몇 명으로 일일 한도가 마른다
 * (1,000건을 관광빅데이터와 나눠 쓴다). 월 1회 배치가 채우고 조회는 DB 만 읽는다.
 *
 * <p><b>분류를 저장한다.</b> {@code lclsSystm1}(대분류)은 목록 응답에 이미 실려 오므로 판정에 추가 호출이
 * 없다. 그 값을 그대로 두지 않고 {@link Category} 로 바꿔 담는 이유는, 조회할 때마다 다시 판정하면 규칙이
 * 바뀌었을 때 저장분과 어긋나기 때문이다 — 필터칩 개수와 목록이 갈리면 "12곳" 이라고 적힌 칩을 눌렀는데
 * 9곳이 나온다.
 */
@Entity
@Getter
@Table(name = "region_poi")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionPoi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어느 지역의 장소인지. 도메인 경계를 넘는 참조라 raw ID 다(영속성 규약). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /** TourAPI contentId — 장소 상세({@code GET /pois/{contentId}})로 그대로 이어진다. */
    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    @Column(name = "content_type_id", nullable = false)
    private int contentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, length = 200)
    private String title;

    /**
     * 대표 사진 — <b>{@code null} 이 정상이다.</b>
     *
     * <p>TourAPI 에 사진이 없는 장소가 있다. "매력 포인트 장소" 는 사진 있는 것만 담으므로 이 값의 유무가
     * 곧 노출 조건이다 — 사진 없는 항목이 섞이면 가로 목록 중간에 회색 판이 낀다.
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 300)
    private String address;

    /**
     * 좌표 — 지역 상세는 안 쓰지만 함께 담는다.
     *
     * <p>같은 목록 응답에 실려 오므로 안 담을 이유가 없고, 나중에 지도를 붙일 때 이 값이 없으면
     * 89곳을 다시 훑어야 한다(외부 한도를 또 태운다). 컬럼 두 개의 비용이 그보다 싸다.
     */
    private Double lat;

    private Double lng;

    @Column(length = 100)
    private String tel;

    /** 갱신 기준월(YYYYMM). 그 달치가 이미 있으면 외부를 아예 안 부른다. */
    @Column(name = "base_ym", nullable = false, length = 6)
    private String baseYm;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Builder
    private RegionPoi(
            Long regionId,
            String contentId,
            int contentTypeId,
            Category category,
            String title,
            String imageUrl,
            String address,
            Double lat,
            Double lng,
            String tel,
            YearMonth baseYm,
            LocalDateTime fetchedAt) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.contentId = requireText(contentId, "장소 ID는 필수입니다");
        this.contentTypeId = contentTypeId;
        this.category = Objects.requireNonNull(category, "분류는 필수입니다");
        this.title = requireText(title, "장소명은 필수입니다");
        this.imageUrl = imageUrl;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.tel = tel;
        this.baseYm = format(Objects.requireNonNull(baseYm, "기준월은 필수입니다"));
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "조회 시각은 필수입니다");
    }

    /** 이 장소를 카드로 내보낼 수 있는가 — <b>사진이 있어야 한다</b>. */
    public boolean showable() {
        return imageUrl != null && !imageUrl.isBlank();
    }

    /** {@code YYYYMM} — 컬럼이 {@code CHAR(6)} 이라 표현을 한 곳에서 정한다. */
    public static String format(YearMonth baseYm) {
        return "%04d%02d".formatted(baseYm.getYear(), baseYm.getMonthValue());
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
