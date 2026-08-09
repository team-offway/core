package com.offway.core.trip.service.dto;

import com.offway.core.common.response.PageResponse;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceCategory;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 지역 장소 목록 조회 결과(#144) — 한 페이지의 장소와 페이지 정보.
 *
 * @param places 이 페이지의 장소들
 * @param page 0부터 시작하는 페이지 번호
 * @param size 페이지 크기
 * @param totalElements 조건에 맞는 전체 건수
 * @param totalPages 전체 페이지 수
 */
public record RegionPlaces(List<Place> places, int page, int size, long totalElements, int totalPages)
        implements PageResponse.Paged {

    public RegionPlaces {
        places = List.copyOf(places);
    }

    public static RegionPlaces from(Page<LicensedPlace> page) {
        return new RegionPlaces(
                page.getContent().stream().map(Place::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * 목록에 실리는 장소 한 건.
     *
     * <p>사진·소개는 없다 — 인허가 데이터가 주지 않는다. 대신 전화번호가 있어(29%) 예약·문의로 바로 이어진다.
     *
     * @param id 공개 식별자. 상세 조회({@code /api/v1/pois/{id}})에 그대로 쓴다
     * @param name 상호
     * @param category 분류 코드
     * @param categoryLabel 분류 표시명(예: 한옥체험)
     * @param address 도로명 주소
     * @param tel 대표 전화. 없으면 null
     * @param lat 위도
     * @param lng 경도
     */
    public record Place(
            String id,
            String name,
            PlaceCategory category,
            String categoryLabel,
            String address,
            String tel,
            double lat,
            double lng) {

        public static Place from(LicensedPlace place) {
            return new Place(
                    place.publicId(),
                    place.getName(),
                    place.getCategory(),
                    place.getCategory().label(),
                    place.getAddress(),
                    place.getTel(),
                    place.getLat(),
                    place.getLng());
        }
    }
}
