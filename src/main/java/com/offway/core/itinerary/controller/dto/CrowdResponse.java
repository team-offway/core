package com.offway.core.itinerary.controller.dto;

import com.offway.core.trip.domain.CrowdChip;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 혼잡 칩 한 개(#565) — 그 칸이 <b>그 날짜</b>에 붐비는가.
 *
 * <p><b>근거를 함께 내린다.</b> 지역 폴백은 그 코스의 장소 전부에 같은 값이 붙으므로, 화면이 그 사실을
 * 알아야 장소 속성처럼 그리지 않는다. 문구도 갈라 둔다("이날 붐빔" vs "토요일엔 붐비는 지역").
 *
 * <p>칩이 없으면 이 블록 자체가 <b>없다</b>(`@JsonInclude(NON_NULL)`). "확인 안 됨" 을 띄우지 않는다 —
 * 안 붐비는 곳과 못 받아온 곳이 화면에서 같아 보이면 안 된다.
 *
 * @param level BUSY(붐빔) · QUIET(한산)
 * @param basis ATTRACTION_FORECAST(그 장소·그 날짜의 집중률 예측) ·
 *     REGION_WEEKDAY(그 지역의 요일 패턴 — <b>같은 코스의 장소에 같은 값이 붙는다</b>)
 * @param label 화면에 그대로 쓰는 문구
 */
public record CrowdResponse(
        @Schema(example = "BUSY", allowableValues = {"BUSY", "QUIET"}) String level,
        @Schema(example = "ATTRACTION_FORECAST",
                allowableValues = {"ATTRACTION_FORECAST", "REGION_WEEKDAY"}) String basis,
        @Schema(example = "이날 붐빔") String label) {

    /** 칩이 없으면 null 이다 — 필드 자체가 응답에서 사라진다. */
    public static CrowdResponse from(CrowdChip chip) {
        if (chip == null) {
            return null;
        }
        return new CrowdResponse(chip.level().name(), chip.basis().name(), chip.label());
    }
}
