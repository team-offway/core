package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관광사진 갤러리 한 장 — 사진작가가 찍은 관광 사진(#196).
 *
 * <p><b>지역 대표 사진의 주력 소스다.</b> TourAPI 의 {@code firstimage} 는 등록 자체가 없는 명소가 많다 —
 * 공주 공산성이 그렇다. 갤러리는 6,118건(실측 2026-08-09)이고 우리 89곳 중 88곳을 덮는다.
 *
 * <p><b>촬영 위치는 믿을 수 없다.</b> 원본이 자유 텍스트라 "전남광주통합특별시"(711건)·"강원도"와
 * "강원특별자치도" 혼재는 물론 "신승반점" 같은 값까지 온다. 그래서 원문을 그대로 보관하고, 우리가 정규화해
 * 붙인 {@code regionId} 를 따로 둔다 — 규칙이 바뀌면 원문에서 다시 매길 수 있다.
 */
@Entity
@Table(name = "gallery_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GalleryPhoto {

    private static final DateTimeFormatter PHOTOGRAPHY_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 갤러리 원본 식별자 — 재적재 때 같은 사진을 알아보는 키. */
    @Column(name = "gal_content_id", nullable = false, length = 32)
    private String galContentId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** 촬영월({@code yyyyMM}) — 없을 수 있다. 여행월과 가까운 사진을 고를 때만 쓴다. */
    @Column(name = "photography_month", length = 6)
    private String photographyMonth;

    /** 촬영 위치 <b>원문</b>. 정규화 결과가 아니라 원본 그대로다. */
    @Column(name = "photography_location", length = 300)
    private String photographyLocation;

    @Column(length = 100)
    private String photographer;

    /**
     * 키워드 묶음 — 장소명 매칭의 절반을 담당한다.
     *
     * <p>제목에 없는 장소명이 여기 있는 경우가 흔하다. 예로 제목이 "금강철교" 인 사진의 키워드에 "공산성" 이
     * 들어 있어, 제목만 보면 공주 대표를 놓친다.
     */
    @Column(name = "search_keyword", length = 2000)
    private String searchKeyword;

    /** 정규화로 붙인 우리 지역. 못 붙였으면 null 이고 대표 사진 후보에서 빠진다. */
    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private GalleryPhoto(
            String galContentId,
            String title,
            String imageUrl,
            String photographyMonth,
            String photographyLocation,
            String photographer,
            String searchKeyword,
            Long regionId,
            LocalDateTime updatedAt) {
        this.galContentId = requireText(galContentId, "갤러리 식별자");
        this.title = requireText(title, "사진 제목");
        this.imageUrl = requireText(imageUrl, "이미지 URL");
        this.photographyMonth = photographyMonth;
        this.photographyLocation = photographyLocation;
        this.photographer = photographer;
        this.searchKeyword = searchKeyword;
        this.regionId = regionId;
        this.updatedAt = Objects.requireNonNull(updatedAt, "갱신 시각은 필수입니다");
    }

    /**
     * 촬영월 — 형식이 어긋나면 <b>없는 것으로</b> 본다.
     *
     * <p>계절 근접도를 계산할 때만 쓰는 값이라, 깨진 값 하나로 사진을 버리는 것보다 계절 정렬에서만 빠지는
     * 편이 낫다.
     */
    public Optional<YearMonth> photographyMonth() {
        if (photographyMonth == null || photographyMonth.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(YearMonth.parse(photographyMonth, PHOTOGRAPHY_MONTH));
        } catch (java.time.format.DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** 이 사진이 어느 지역 것인지 정한다 — 적재 후 정규화가 붙인다. */
    public void assignRegion(Long regionId) {
        this.regionId = regionId;
    }

    /** 제목과 키워드를 합친 매칭 대상 — 장소명이 둘 중 어디에 있을지 모른다. */
    public String matchableText() {
        return title + " " + (searchKeyword == null ? "" : searchKeyword);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + "은(는) 필수입니다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + "은(는) 비어 있을 수 없습니다");
        }
        return value;
    }
}
