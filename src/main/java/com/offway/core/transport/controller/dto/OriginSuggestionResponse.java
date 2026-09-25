package com.offway.core.transport.controller.dto;

import com.offway.core.transport.service.dto.OriginSuggestion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 출발지 제안 한 줄 — API 계약(#590).
 *
 * @param code 코스 생성·추천에 그대로 실어 보낼 값. <b>앱은 좌표를 다루지 않는다</b>
 * @param name 화면에 뜨는 이름
 * @param area 부제목 — 역·터미널은 시도, 주소는 주소 문자열
 * @param kind 아이콘을 가르는 값
 */
@Schema(description = "출발지 제안 한 줄")
public record OriginSuggestionResponse(
        @Schema(
                        description = "코스 생성·추천의 originCode 에 그대로 넣는다. 형태는 서버 소관이라 앱은 해석하지 않는다",
                        example = "TRAIN:NAT010000",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String code,
        @Schema(description = "화면에 뜨는 이름", example = "서울역", requiredMode = Schema.RequiredMode.REQUIRED)
                String name,
        @Schema(
                        description = "부제목. 역·터미널은 시도(서울·경기), 주소는 주소 문자열",
                        example = "서울",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String area,
        @Schema(
                        description = """
                                아이콘을 가르는 값.

                                - `TRAIN_STATION` 기차역
                                - `BUS_TERMINAL` 버스 터미널
                                - `ADDRESS` 우리 허브가 아닌 주소·장소. 고르면 서버가 가장 가까운 역·터미널을 찾아 코스를 짠다

                                `ADDRESS` 를 고를 때는 화면에 보인 `name` 을 `originName` 으로 함께 보내 주세요 —
                                그 지점에는 우리 이름이 없어, 카드의 "어디에서 출발" 을 그릴 값이 없습니다.""",
                        example = "TRAIN_STATION",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                OriginSuggestion.Kind kind) {

    public static OriginSuggestionResponse from(OriginSuggestion suggestion) {
        return new OriginSuggestionResponse(
                suggestion.code().value(), suggestion.name(), suggestion.area(), suggestion.kind());
    }

    public static List<OriginSuggestionResponse> from(List<OriginSuggestion> suggestions) {
        return suggestions.stream().map(OriginSuggestionResponse::from).toList();
    }
}
