package com.offway.core.trip.infrastructure.festival.dto;

import com.offway.core.trip.domain.FestivalPlace;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 전국문화축제표준데이터 한 건(#433).
 *
 * <p>주소는 <b>도로명을 먼저 쓰고 없으면 지번</b>이다. 지자체마다 채우는 칸이 달라 하나만 보면 빈다.
 *
 * @param sigunguName 소재지 시군구명 — 우리 89곳에 붙일 때 쓴다. 주소에서 뽑는다
 * @param lat 위도. <b>없을 수 있다</b>(446건 중 101건) — 그때는 후보로 못 쓴다
 * @param lng 경도
 */
public record StandardFestival(
        String name,
        String venue,
        String address,
        String sigunguName,
        Double lat,
        Double lng,
        LocalDate eventStart,
        LocalDate eventEnd,
        String description,
        String host,
        String tel,
        String homepageUrl) {

    /** 코스에 올릴 수 있는가 — 이름·기간·좌표가 다 있어야 한다. */
    public boolean isUsable() {
        return name != null && !name.isBlank()
                && eventStart != null && eventEnd != null && !eventStart.isAfter(eventEnd)
                && lat != null && lng != null
                && address != null && !address.isBlank();
    }

    /**
     * 우리 도메인으로 옮긴다. <b>{@link #isUsable()} 이 참일 때만</b> 부른다 — 아니면 엔티티 불변식이
     * 예외를 던진다.
     */
    public FestivalPlace toPlace(long regionId, LocalDateTime fetchedAt) {
        return FestivalPlace.builder()
                .regionId(regionId)
                .name(name)
                .venue(venue)
                .address(address)
                .lat(lat)
                .lng(lng)
                .eventStart(eventStart)
                .eventEnd(eventEnd)
                .description(description)
                .host(host)
                .tel(tel)
                .homepageUrl(homepageUrl)
                .fetchedAt(fetchedAt)
                .build();
    }
}
