package com.offway.core.region.domain;

import com.offway.core.common.geo.Coordinate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
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

    /** 알림 문구가 쓰는 짧은 이름에서 떼는 접미사. <b>{@code 구} 는 넣지 않는다</b> — 아래 참고. */
    private static final List<String> TRIMMABLE_SUFFIXES = List.of("군", "시");

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
     *
     * <p><b>전남 16곳의 코드를 새 체계로 고치지 마라</b>(#347). 행정구역이 개편돼 전라남도·광주광역시가
     * <b>전남광주통합특별시</b>(시도코드 12)로 합쳐졌고 법정코드도 {@code 46xxx → 12xxx} 로 바뀌었다. 그래서
     * 낡아 보이지만, <b>우리가 부르는 API 는 아직 옛 코드만 답한다.</b> 실측(2026-08-28)이다.
     *
     * <pre>
     *   고흥 @202606   46770 → 100건 ✅      12740 → 0건 ❌
     *   강진 @202606   46810 →  80건 ✅      12780 → 0건 ❌
     * </pre>
     *
     * <p>고치면 <b>전남 16곳의 중심 관광지가 통째로 빈다.</b> 게다가 빈 응답은 예외가 아니라 폴백으로
     * 흡수돼 아무 흔적을 안 남긴다 — 코스 품질이 조용히 떨어진다. 옮기는 시점은 그쪽이 옮긴 뒤다.
     * 그날이 오면 {@code area_code}·{@code sigungu_code} 도 함께 봐야 한다({@code areaCode2} 는 지금도
     * {@code 38 전라남도} 를 준다).
     */
    @Column(name = "legal_code", nullable = false, length = 5)
    private String legalCode;

    /**
     * 문장에 넣을 짧은 지명 — {@code 정선군} → {@code 정선}(#356).
     *
     * <p>알림 문구가 <b>'정선 여행'</b> 처럼 읽혀야 하는데 {@code 정선군 여행} 은 어색하다. 그래서 접미사를
     * 떼는데, <b>{@code 구} 는 떼지 않는다.</b>
     *
     * <p>89곳 중 다섯이 자치구다(남구·동구·서구 둘·영도구). 무턱대고 한 글자를 떼면 {@code 동구} 가
     * {@code 동}, {@code 남구} 가 {@code 남} 이 된다 — 지명이 아니게 된다. {@code 영도구} 만 {@code 영도} 로
     * 말이 되는데, 그 하나를 위해 나머지 넷을 깨뜨릴 이유가 없다. {@code 동구 여행} 은 그대로도 읽힌다.
     *
     * <p><b>서버가 다듬어 내려보내는 이유</b> — 앱이 접미사를 떼면 그 다섯 곳에서 같은 함정을 밟는다.
     * 어느 이름이 자치구인지는 이 표를 가진 쪽만 안다.
     *
     * <p>시도는 붙이지 않는다. {@code #348} 로 전남 16곳이 {@code 전남광주통합특별시} 가 되면서 시도까지
     * 붙은 이름은 15자가 됐다 — 배너 한 줄에 들어가지 않는다.
     */
    public String shortName() {
        for (String suffix : TRIMMABLE_SUFFIXES) {
            if (sigungu.length() > suffix.length() && sigungu.endsWith(suffix)) {
                return sigungu.substring(0, sigungu.length() - suffix.length());
            }
        }
        return sigungu;
    }

    /**
     * 대표 좌표를 값객체로(#404).
     *
     * <p><b>두 칸을 따로 꺼내지 않는다.</b> {@code new Coordinate(region.getLat(), region.getLng())} 를
     * 부르는 곳이 여럿인데, 인자가 같은 타입 둘이라 뒤바꿔도 컴파일이 통과한다 — 위경도가 뒤집히면
     * 거리 계산이 조용히 틀린 값을 내고, 그게 도달시간·추천 순서까지 흘러간다.
     *
     * <p>{@link Coordinate} 생성자가 범위를 검증하므로 여기서 다시 보지 않는다. 두 칸 모두
     * {@code NOT NULL} 이라 언박싱도 안전하다.
     */
    public Coordinate coordinate() {
        return new Coordinate(lat, lng);
    }
}
