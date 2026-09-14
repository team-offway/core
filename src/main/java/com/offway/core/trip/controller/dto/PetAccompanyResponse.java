package com.offway.core.trip.controller.dto;

import com.offway.core.trip.service.dto.PetAccompany;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장소의 반려동반 정보(#566) — 슬롯 모달의 "반려동물 동반" 칩과 그것을 눌렀을 때 열리는 내용.
 *
 * <p><b>이 객체가 있으면 칩을 띄운다.</b> 없으면(null) 띄우지 않는다 — 반려동반이 아닌 곳과 판정할 수
 * 없는 곳(인허가·국가유산·고캠핑 출처)이 함께 여기 해당한다. <b>"반려동물 안 됨" 을 말하는 것이
 * 아니다</b>: 모르는 것을 불가로 내리면 사용자가 갈 수 있는 곳을 포기한다.
 *
 * <p><b>칩 문구는 {@code wholeArea} 로 가른다.</b> 실측(2026-09-13, 태안 15건 전수)에서 절반이
 * "일부구역 동반가능" 이었다. "반려동물 동반" 하나로 뭉뚱그리면 전 구역으로 오해하고, 가서야 못
 * 들어가는 구역을 만난다.
 *
 * <p>안의 값들은 각각 <b>없을 수 있다</b>. 특히 시설·품목은 대개 빈다(실측 15건 중 1건만 채워짐) —
 * 화면은 그 칸을 접어야 한다. 빈 칸을 남기면 "정보가 있는데 못 받아왔다" 처럼 보인다.
 *
 * @param wholeArea 전 구역 동반 가능 여부. 거짓이면 구역 제한이 있다
 * @param area 동반 구역 원문 — "전구역 동반가능" / "일부구역 동반가능"
 * @param pet 동반 가능 반려동물 — "전 견종 동반 가능" / "5kg 이내 소형견"
 * @param requiredMatter 필요 사항 — "목줄 착용"
 * @param etcInfo 기타 동반 정보 — 줄바꿈(`\n`)이 섞여 온다
 * @param riskMatter 사고 위험 사항
 * @param facilities 보유 시설. 대개 null
 * @param providedItems 제공 품목. 대개 null
 * @param rentalItems 대여 품목. 대개 null
 */
public record PetAccompanyResponse(
        @Schema(description = "전 구역 동반 가능 여부. false 면 구역 제한이 있다", example = "true")
                boolean wholeArea,
        @Schema(example = "전구역 동반가능", nullable = true) String area,
        @Schema(example = "전 견종 동반 가능", nullable = true) String pet,
        @Schema(example = "목줄 착용", nullable = true) String requiredMatter,
        @Schema(
                        description = "기타 동반 정보. 줄바꿈이 섞여 온다",
                        example = "- 맹견의 경우, 입마개 착용 필수\n- 배변봉투 지참 및 배변처리 필수",
                        nullable = true)
                String etcInfo,
        @Schema(nullable = true) String riskMatter,
        @Schema(description = "보유 시설. 대개 null 이다", nullable = true) String facilities,
        @Schema(description = "제공 품목. 대개 null 이다", nullable = true) String providedItems,
        @Schema(description = "대여 품목. 대개 null 이다", nullable = true) String rentalItems) {

    /** 반려동반이 아니거나 판정할 수 없는 장소는 <b>{@code null}</b> — 칩을 띄우지 않는다. */
    public static PetAccompanyResponse from(PetAccompany accompany) {
        if (accompany == null) {
            return null;
        }
        return new PetAccompanyResponse(
                accompany.wholeArea(),
                accompany.area(),
                accompany.pet(),
                accompany.requiredMatter(),
                accompany.etcInfo(),
                accompany.riskMatter(),
                accompany.facilities(),
                accompany.providedItems(),
                accompany.rentalItems());
    }
}
