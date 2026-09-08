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

    /**
     * 코스에 올릴 수 있는가 — 이름·기간·주소가 있고 <b>좌표가 쓸 만한가</b>.
     *
     * <p>좌표는 있고 없고만 보지 않는다. 지자체가 {@code 0.0} 이나 범위 밖 값을 올린 행이 있는데,
     * 그걸 {@link #toPlace} 로 넘기면 엔티티 불변식이 예외를 던져 <b>그달 적재가 통째로 실패한다</b>.
     * 한 건만 건너뛰면 될 일이라 여기서 먼저 가른다.
     *
     * <p>범위 판정은 {@link FestivalPlace#isInKorea} 가 소유한다 — 같은 숫자를 두 곳에 적으면
     * 한쪽만 바뀐다.
     */
    public boolean isUsable() {
        return name != null && !name.isBlank()
                && eventStart != null && eventEnd != null && !eventStart.isAfter(eventEnd)
                && lat != null && lng != null && FestivalPlace.isInKorea(lat, lng)
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
