package com.offway.core.trip.controller.dto;

import com.offway.core.transport.domain.OriginCode;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.dto.ResolvedOrigin;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.service.dto.RecommendRegions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 여행지 추천 요청 — API 계약.
 *
 * @param originCode 출발지 코드(#590) — `GET /api/v1/origins` 가 준 값. 있으면 좌표보다 우선한다
 * @param originName 출발지 이름 — 주소·장소를 골랐을 때만
 * @param originLat 출발지 위도. <b>구버전 앱용</b> — originCode 가 없을 때만 쓴다
 * @param originLng 출발지 경도. originLat 와 짝
 * @param transport 이동수단 (CAR·TRANSIT)
 * @param maxReachMinutes 편도 도달 한계(분) — 가용시간(LNT) 산출 응답에서 받아 넘긴다
 * @param mood 무드칩(선택) — 지정 시 해당 볼거리가 있는 지역을 앞세운다. 미지정·ALL 은 필터 없음
 */
public record RegionRecommendRequest(
        @Schema(
                        description = """
                                출발지 코드 — `GET /api/v1/origins` 가 준 `code` 를 그대로 넣는다(#590).

                                이 값이 있으면 `originLat`·`originLng` 는 무시된다. 못 풀면
                                400(TRANSPORT-001)이다 — 조용히 기본 출발지로 바꾸면 고른 곳과 다른 데를
                                기준으로 추천이 나오고, 틀렸다는 사실이 아무 흔적도 남지 않는다.""",
                        example = "BUS:NAEK030",
                        nullable = true)
                @Size(max = OriginCode.MAX_LENGTH) String originCode,
        @Schema(description = "출발지 이름 — kind 가 ADDRESS 인 제안을 골랐을 때만", example = "분당구청", nullable = true)
                @Size(max = 64) String originName,
        @Schema(example = "37.49", description = "구버전 앱용 — originCode 가 없을 때만 쓴다", nullable = true)
                @DecimalMin("-90") @DecimalMax("90") Double originLat,
        @Schema(example = "127.02", description = "originLat 와 짝", nullable = true)
                @DecimalMin("-180") @DecimalMax("180") Double originLng,
        @Schema(example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TransportMode transport,
        @Schema(description = "편도 도달 한계(분)", example = "420", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @Positive Integer maxReachMinutes,
        @Schema(description = "무드칩(선택) — SIGHT·STAY·EXPERIENCE·FOOD", example = "FOOD") Category mood) {

    public RecommendRegions toCommand(ResolvedOrigin origin) {
        // 위도·경도를 이름으로 적는다 — 위치 인수면 둘이 뒤바뀌어도 컴파일이 통과한다(#300).
        return RecommendRegions.builder()
                .originLat(origin.coordinate().lat())
                .originLng(origin.coordinate().lng())
                .transport(transport)
                .maxReachMinutes(maxReachMinutes)
                .mood(mood)
                .build();
    }
}
