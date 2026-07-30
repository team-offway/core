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

    /**
     * 법정 시군구코드(행정표준코드 5자리, 예 {@code 51820}) — 관광빅데이터 방문자수 매칭 키.
     *
     * <p>{@link #sigungu} 지명으로 매칭하면 안 된다. 전국에 동구 6곳·중구 6곳·서구 5곳·남구 4곳·북구 4곳·고성군
     * 2곳이 있어 서로 다른 지역의 방문자가 한 지역으로 합산된다. 우리 89곳 중에도 6곳이 여기 걸린다.
     */
    @Column(name = "legal_code", nullable = false, length = 5)
    private String legalCode;
}
