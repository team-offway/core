package com.offway.core.trip.infrastructure.tour;

import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
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

    /** 소개정보(detailIntro2) — 운영시간·휴무일. 없으면 빈 Optional. */
    Optional<TourIntro> findIntro(String contentId, int contentTypeId);
}
