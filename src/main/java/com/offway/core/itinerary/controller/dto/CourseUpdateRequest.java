package com.offway.core.itinerary.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 저장 코스 수정 요청 — API 계약(#170). 편집 시트의 "여행날짜 수정" 이 쓴다.
 *
 * <p>고칠 수 있는 것은 지금 여행 시작일뿐이라 <b>필수</b>다. PATCH 지만 "생략하면 그대로" 로 두지 않는다 —
 * 빈 body 를 200 으로 받아주면 아무것도 안 바뀐 응답이 성공처럼 보인다.
 *
 * <p>기간(travelDays)·이동수단은 여기서 바꾸지 않는다. 그것들이 바뀌면 코스 구성 자체를 다시 짜야 하므로
 * 생성({@code POST /courses/generate})으로 가는 것이 맞다.
 *
 * @param travelDate 옮겨갈 여행 시작일. 지난 날짜는 거절한다(400)
 */
public record CourseUpdateRequest(
        @Schema(
                        description = "옮겨갈 여행 시작일. 오늘 이후여야 한다.",
                        example = "2026-08-14",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                LocalDate travelDate) {
}
