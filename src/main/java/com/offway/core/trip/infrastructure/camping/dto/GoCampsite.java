package com.offway.core.trip.infrastructure.camping.dto;

import com.offway.core.common.geo.KoreaBounds;
import com.offway.core.trip.domain.CampingPlace;
import java.time.LocalDateTime;

/**
 * 고캠핑 야영장 한 건(#510).
 *
 * @param externalId 고캠핑 {@code contentId} — 자연키. TourAPI 것과 <b>체계가 다르다</b>(교집합 0)
 * @param address 소재지. 지역 매칭에 이 값을 쓴다 — 99% 가 채워진다
 * @param lat 위도({@code mapY}). <b>없을 수 있다</b>(3,115건 중 10건)
 * @param lng 경도({@code mapX})
 * @param operating 운영 중인가({@code manageSttus}) — 휴장 123건을 여기서 가른다
 */
public record GoCampsite(
        String externalId,
        String name,
        String address,
        String sigunguName,
        Double lat,
        Double lng,
        String induty,
        String imageUrl,
        String lineIntro,
        String intro,
        String tel,
        String homepageUrl,
        String operPeriod,
        String operDays,
        String reservation,
        boolean operating) {

    /**
     * 코스에 올릴 수 있는가 — <b>운영 중이고</b> 이름·주소가 있고 좌표가 쓸 만한가.
     *
     * <p><b>휴장을 여기서 막는다.</b> 휴장한 야영장을 코스에 넣으면 여행자가 헛걸음한다. 상태를 저장한 뒤
     * 조회마다 거르는 대신 담기 전에 가른다 — 그러면 표에 있는 것은 전부 쓸 수 있는 것이 된다.
     *
     * <p>좌표는 있고 없고만 보지 않는다. 출처가 {@code 0.0} 이나 범위 밖 값을 올린 행이 있는데, 그걸
     * {@link #toPlace} 로 넘기면 엔티티 불변식이 예외를 던져 <b>그달 적재가 통째로 실패한다</b>.
     * 한 건만 건너뛰면 될 일이라 여기서 먼저 가른다.
     */
    public boolean isUsable() {
        return operating
                && externalId != null && !externalId.isBlank()
                && name != null && !name.isBlank()
                && address != null && !address.isBlank()
                && lat != null && lng != null && KoreaBounds.contains(lat, lng);
    }

    /**
     * 우리 도메인으로 옮긴다. <b>{@link #isUsable()} 이 참일 때만</b> 부른다 — 아니면 엔티티 불변식이
     * 예외를 던진다.
     */
    public CampingPlace toPlace(long regionId, LocalDateTime fetchedAt) {
        return CampingPlace.builder()
                .regionId(regionId)
                .externalId(externalId)
                .name(name)
                .address(address)
                .lat(lat)
                .lng(lng)
                .induty(induty)
                .imageUrl(imageUrl)
                .lineIntro(lineIntro)
                .intro(intro)
                .tel(tel)
                .homepageUrl(homepageUrl)
                .operPeriod(operPeriod)
                .operDays(operDays)
                .reservation(reservation)
                .fetchedAt(fetchedAt)
                .build();
    }
}
