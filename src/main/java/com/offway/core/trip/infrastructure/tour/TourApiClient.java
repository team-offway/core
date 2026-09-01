package com.offway.core.trip.infrastructure.tour;

import com.offway.core.trip.infrastructure.tour.dto.TourAccessibility;
import com.offway.core.trip.infrastructure.tour.dto.TourFestivalResult;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 국문 관광정보(TourAPI · KorService2) 조회 port.
 *
 * <p>trip 도메인·서비스는 이 인터페이스에만 의존한다. 실제 외부 호출은 adapter 가 맡는다(DIP). 키가 없으면 외부 호출 없이 빈 결과를
 * 돌려준다(로컬 실행성 불변식).
 */
public interface TourApiClient {

    /**
     * 지역기반 관광정보 목록(areaBasedList2).
     *
     * @param areaCode TourAPI 지역코드 (필수)
     * @param sigunguCode 시군구코드 (선택, areaCode 와 함께만 유효)
     * @param contentTypeId 콘텐츠 타입 (선택)
     * @param numOfRows 페이지 크기
     */
    TourPoiResult findByArea(int areaCode, Integer sigunguCode, Integer contentTypeId, int numOfRows);

    /**
     * 위치기반 관광정보 목록(locationBasedList2) — 좌표 반경 내.
     *
     * @param lat 위도
     * @param lng 경도
     * @param radiusMeters 반경(m)
     * @param contentTypeId 콘텐츠 타입 (선택)
     * @param numOfRows 페이지 크기
     */
    TourPoiResult findByLocation(double lat, double lng, int radiusMeters, Integer contentTypeId, int numOfRows);

    /**
     * 행사정보(searchFestival2) — <b>축제가 언제 열리는지</b>(#388).
     *
     * <p>{@link #findByArea} 는 이 날짜를 주지 않는다. 그래서 축제가 볼거리 풀에 들어와 있으면서도
     * 기간을 모르는 상태였고, 끝난 축제가 코스에 들어갈 수 있었다.
     *
     * <p><b>지역을 받지 않는다.</b> 89곳을 각각 부르면 회차마다 89번인데, 전국을 한 번에 받아 우리
     * 쪽에서 {@code contentId} 로 맞추면 <b>페이지 수만큼</b>이면 된다. 지역별 조회가 필요해지는 날
     * 그때 파라미터를 연다.
     *
     * @param from 이 날짜 <b>이후</b>에 시작하는 행사만. TourAPI 의 {@code eventStartDate}
     * @param pageNo 1부터
     * @param numOfRows 페이지 크기
     * @return 이번 페이지 + 전체 건수. 전체 건수가 남은 비용을 확정한다
     */
    TourFestivalResult findFestivals(LocalDate from, int pageNo, int numOfRows);

    /** 소개정보(detailIntro2) — 운영시간·휴무일. 없으면 빈 Optional. */
    Optional<TourIntro> findIntro(String contentId, int contentTypeId);

    /** 공통 상세(detailCommon2) — 장소 기본정보(이름·주소·이미지·소개). 없으면 빈 Optional. */
    Optional<TourPoiDetail> findDetail(String contentId);

    /**
     * 무장애정보(KorWithService2 · detailWithTour2) — 이용약자 편의. 등록 정보가 없으면 빈 Optional
     * (TourAPI 는 정상 응답으로 0건을 준다 — 조회 실패와 구분한다).
     */
    Optional<TourAccessibility> findAccessibility(String contentId);
}
