package com.offway.core.trip.infrastructure.pet.dto;

import com.offway.core.trip.domain.PetFriendlyPlace;
import java.time.LocalDateTime;

/**
 * 반려동반 상세 한 건(#566) — {@code detailPetTour2} 가 주는 동반 조건·유의사항·이용 가능 시설.
 *
 * <p><b>이 상세가 있어야 칩이 정직해진다.</b> 실측(2026-09-13, 태안 15건 전수)에서 절반이
 * "일부구역 동반가능" 이었고 체중 제한도 셋 있었다. "반려동물 동반" 칩만 띄우면 그 조건이 가려진다.
 *
 * @param contentId TourAPI 콘텐츠 ID
 * @param accompanyArea {@code acmpyTypeCd} — 전구역/일부구역 동반가능
 * @param accompanyPet {@code acmpyPsblCpam} — 전 견종 / 5kg 이내 소형견 등
 * @param requiredMatter {@code acmpyNeedMtr} — 목줄 착용 등
 * @param etcInfo {@code etcAcmpyInfo} — 줄바꿈이 섞여 온다
 * @param riskMatter {@code relaAcdntRiskMtr} — 사고 위험 사항
 * @param facilities {@code relaPosesFclty} — 보유 시설. <b>대개 빈다</b>
 * @param providedItems {@code relaFrnshPrdlst} — 제공 품목. 대개 빈다
 * @param rentalItems {@code relaRntlPrdlst} — 대여 품목. 대개 빈다
 */
public record PetTourDetail(
        String contentId,
        String accompanyArea,
        String accompanyPet,
        String requiredMatter,
        String etcInfo,
        String riskMatter,
        String facilities,
        String providedItems,
        String rentalItems) {

    /** 상세를 못 받은 장소용 — 조건을 하나도 모르는 상태. 칩은 띄우되 열 내용이 없다. */
    public static PetTourDetail unknown(String contentId) {
        return new PetTourDetail(contentId, null, null, null, null, null, null, null, null);
    }

    /**
     * 목록의 지역·이름과 합쳐 엔티티로 — <b>매핑은 DTO 자신이 든다</b>(별도 Mapper 를 만들지 않는다).
     *
     * <p>상세만으로는 엔티티가 안 된다. 어느 지역인지와 장소명은 목록이 알기 때문이다.
     */
    public PetFriendlyPlace toPlace(Long regionId, String name, LocalDateTime fetchedAt) {
        return PetFriendlyPlace.builder()
                .contentId(contentId)
                .regionId(regionId)
                .name(name)
                .accompanyArea(accompanyArea)
                .accompanyPet(accompanyPet)
                .requiredMatter(requiredMatter)
                .etcInfo(etcInfo)
                .riskMatter(riskMatter)
                .facilities(facilities)
                .providedItems(providedItems)
                .rentalItems(rentalItems)
                .fetchedAt(fetchedAt)
                .build();
    }
}
