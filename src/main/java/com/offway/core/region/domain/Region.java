package com.offway.core.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인구감소지역(행안부 고시 89곳). 추천 대상 지역의 마스터 데이터. */
@Entity
@Table(name = "region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String sido;

    @Column(nullable = false, length = 40)
    private String sigungu;

    @Column(name = "notice_date", nullable = false)
    private LocalDate noticeDate;

    @Column(name = "source_url")
    private String sourceUrl;

    /** 대표 좌표(관청, WGS84) — 도달시간 계산·주변 조회의 기준점. */
    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lng", nullable = false)
    private Double lng;

    /** TourAPI KorService2 시도 코드 (areaBasedList2 areaCode). */
    @Column(name = "area_code", nullable = false)
    private Integer areaCode;

    /** TourAPI KorService2 시군구 코드 (areaBasedList2 sigunguCode). */
    @Column(name = "sigungu_code", nullable = false)
    private Integer sigunguCode;
}
