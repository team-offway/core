package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.transport.domain.OriginCode;
import com.offway.core.transport.service.dto.ResolvedOrigin;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.domain.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 코스 생성 요청 — API 계약. 후보지역(추천)에서 지역을 고른 뒤 위저드 값(일수·밀도·이동수단·가는날)과 함께 넘어온다.
 *
 * @param regionId 코스를 만들 지역
 * @param travelDays 여행 일수(1~3, 최대 2박3일)
 * @param density 일정 밀도(PACKED 빡빡 / RELAXED 널널)
 * @param transport 이동수단(CAR·TRANSIT)
 * @param originCode 출발지 코드(#590) — `GET /api/v1/origins` 가 준 값. 있으면 좌표보다 우선한다
 * @param originName 출발지 이름 — 주소·장소를 골랐을 때만. 역·터미널은 서버가 안다
 * @param originLat 출발지 위도(동선 정렬 기준). <b>구버전 앱용</b> — originCode 가 없을 때만 쓴다
 * @param originLng 출발지 경도. originLat 와 짝
 * @param travelDate 가는 날(정책 운영기간 매칭)
 * @param startDayLeave 첫날에 쓴 연차 (선택, 기본 FULL_DAY). 출발 시각이 여기서 도출돼 첫날 일정을 자른다(#138)
 */
public record CourseGenerateRequest(
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long regionId,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @Min(1) @Max(Course.MAX_TRAVEL_DAYS) Integer travelDays,
        @Schema(example = "PACKED", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Density density,
        @Schema(example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TransportMode transport,
        @Schema(
                        description = """
                                출발지 코드 — `GET /api/v1/origins` 가 준 `code` 를 그대로 넣는다(#590).

                                앱이 GPS 수집을 그만두면서 사용자가 출발지를 직접 고른다. 이 값이 있으면
                                `originLat`·`originLng` 는 무시된다.

                                **못 풀면 400(TRANSPORT-001)이다** — 조용히 기본 출발지로 바꾸면 고른 곳과
                                다른 데서 출발하는 코스가 나오고, 틀렸다는 사실이 아무 흔적도 남지 않는다.""",
                        example = "TRAIN:NAT010000",
                        nullable = true)
                @Size(max = OriginCode.MAX_LENGTH) String originCode,
        @Schema(
                        description = """
                                출발지 이름 — `kind` 가 `ADDRESS` 인 제안을 골랐을 때만 보낸다.

                                역·터미널은 서버가 이름을 알고 있어 무시된다. 주소·장소는 우리 이름이 없어,
                                카드의 "어디에서 출발" 을 그릴 값이 여기서 온다.""",
                        example = "분당구청",
                        nullable = true)
                @Size(max = 64) String originName,
        @Schema(example = "37.49", nullable = true)
                @DecimalMin("-90") @DecimalMax("90") Double originLat,
        @Schema(example = "127.02", nullable = true)
                @DecimalMin("-180") @DecimalMax("180") Double originLng,
        @Schema(example = "2026-05-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate travelDate,
        @Schema(description = """
                이 수단으로 코스를 짠다(#453). 카드에서 수단 칩을 눌렀을 때만 보낸다.

                **도착 지점이 바뀌면 동선도 바뀐다** — 코스는 집이 아니라 내린 곳에서 시작하므로,
                시간표만 갈아끼우면 순서와 첫날 시각이 어긋난 채 남는다. 지역에 따라 역과 터미널이
                수십 km 떨어져 있다(양양은 강릉역까지 42km).

                그 지역에 그 수단이 안 닿으면 **서버가 고른 수단으로 돌아간다.**""",
                example = "TRAIN", nullable = true)
                TransitMode transitMode,
        @Schema(
                        description = "첫날에 쓴 연차 (선택, 기본 FULL_DAY). 출발 시각이 여기서 나오고 그 시각이 "
                                + "첫날 일정을 자른다 — FULL_DAY 08시 · HALF_DAY 12시 · QUARTER_DAY 15시",
                        example = "HALF_DAY",
                        nullable = true)
                StartDayLeave startDayLeave) {

    public GenerateCourse toCommand(ResolvedOrigin origin) {
        // 씨앗과 제외 목록을 안 적으면 그대로 첫 생성이다 — seed 는 FIRST_SEED(0), 제외는 빈 집합.
        return GenerateCourse.builder()
                .regionId(regionId)
                .travelDays(travelDays)
                .density(density)
                .transport(transport)
                .transitMode(transitMode)
                // 출발지는 **컨트롤러가 이미 풀어 준 값**을 쓴다. originCode·좌표·기본값 중 어느
                // 것이었는지는 여기서 알 필요가 없다 — 우선순위는 OriginSuggestService 가 소유한다.
                .originLat(origin.coordinate().lat())
                .originLng(origin.coordinate().lng())
                .travelDate(travelDate)
                .startDayLeave(startDayLeave)
                .build();
    }
}
