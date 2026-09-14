package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.PetFriendlyPlace;

/**
 * 슬롯 하나의 반려동반 정보(#566) — trip 이 적재한 것을 다른 도메인(itinerary)에 넘기는 값.
 *
 * <p><b>엔티티를 응답 경계까지 넘기지 않는다</b>(DTO 2계층). 판정은 {@link PetFriendlyPlace} 가 소유하고
 * 여기는 그 결과를 실어 나른다.
 *
 * @param wholeArea 전 구역에 데려갈 수 있는가 — <b>거짓이면 구역 제한이 있다.</b> 실측에서 절반이
 *     "일부구역" 이었으므로 화면이 이 값으로 칩 문구를 가른다
 * @param area {@code acmpyTypeCd} 원문 — "전구역 동반가능" / "일부구역 동반가능"
 * @param pet 동반 가능 반려동물 — "전 견종 동반 가능" / "5kg 이내 소형견" 등
 * @param requiredMatter 필요 사항 — "목줄 착용" 등
 * @param etcInfo 기타 동반 정보 — 줄바꿈이 섞여 온다
 * @param riskMatter 사고 위험 사항
 * @param facilities 보유 시설. <b>대개 null</b>(실측 15건 중 1건만 채워짐)
 * @param providedItems 제공 품목. 대개 null
 * @param rentalItems 대여 품목. 대개 null
 */
public record PetAccompany(
        boolean wholeArea,
        String area,
        String pet,
        String requiredMatter,
        String etcInfo,
        String riskMatter,
        String facilities,
        String providedItems,
        String rentalItems) {

    /** 적재된 장소를 넘길 값으로 — <b>매핑은 DTO 자신이 든다</b>(별도 Mapper 를 만들지 않는다). */
    public static PetAccompany from(PetFriendlyPlace place) {
        return new PetAccompany(
                place.allowsWholeArea(),
                place.getAccompanyArea(),
                place.getAccompanyPet(),
                place.getRequiredMatter(),
                place.getEtcInfo(),
                place.getRiskMatter(),
                place.getFacilities(),
                place.getProvidedItems(),
                place.getRentalItems());
    }
}
