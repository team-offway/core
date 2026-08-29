package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.itinerary.controller.dto.CourseGenerateRequest;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.controller.dto.CourseRegenerateResponse;
import com.offway.core.itinerary.controller.dto.CourseRegenerateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 코스 생성 API 문서 계약. 매핑·검증은 구현체({@link CourseGenerateController})가 소유한다. */
@Tag(name = "코스", description = "지역+조건으로 날짜별 타임라인 코스 자동 생성")
public interface CourseGenerateApi {

    @Operation(
            summary = "코스 자동 생성",
            description = "지역·일수·밀도·이동수단으로 날짜별 타임라인(관광·식사·숙박 슬롯 + 이동시간 + 지도 좌표)과 적용 혜택을 만든다."
                    + " 동선은 이동수단 기반 최근접 정렬(자차 기준 interim)로 배치한다."
                    + " `curatedLinks` 는 코스 화면에 켜진 외부 링크(#341) — 없으면 빈 목록이고,"
                    + " description·thumbnailUrl 은 null 일 수 있다.")
    @ApiResponse(responseCode = "200", description = "생성 성공")
    @ApiResponse(
            responseCode = "400",
            description = "지역 누락 · 일수 범위(1~3) 초과 · 좌표 범위 초과 · 필수값 누락"
                    + " · 첫날 연차 단위가 목록에 없는 값(FULL_DAY·HALF_DAY·QUARTER_DAY)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    @ApiResponse(responseCode = "404", description = "해당 지역으로 만들 수 있는 코스가 없음(볼거리 부족)")
    @ApiResponse(responseCode = "502", description = "관광정보(TourAPI) 조회 실패")
    ApiResponseBody<CourseResponse> generate(CourseGenerateRequest request);

    @Operation(
            summary = "코스 재생성 — 기존과 다른 코스로",
            description = """
                    같은 조건으로 **다른 코스**를 짠다. 생성은 결정론적이라 그냥 다시 부르면 같은 코스가 나오므로,
                    이 API 는 후보 선택의 씨앗을 바꿔가며 직전 코스와 겹치지 않는 조합을 찾는다.

                    보태는 값은 **전부 선택**이다. 아무것도 안 주면 무작위로 다시 짠다.

                    - `seed` — 씨앗을 직접 고를 때. **재현이 목적**이라 이 값을 주면 서버가 씨앗을 바꿔가며 시도하지 않는다
                    - `previousSeed` — 지금 화면에 떠 있는 코스의 씨앗. 이것과 다른 코스를 만들려고 쓴다
                    - `excludePoiContentIds` — 빼고 짤 장소들("이 장소 말고")

                    **후보가 적으면 다르게 만들 수 없다.** 인구감소지역은 볼거리 후보가 필요 개수와 비슷한 경우가
                    흔하다. 그때 `differentFromPrevious` 가 거짓으로 내려간다 — 조용히 같은 코스를 주면 사용자는
                    버튼이 고장 난 줄 알기 때문이다.

                    응답의 `seed` 를 다음 재생성에 `previousSeed` 로 넘기면 계속 다른 코스를 받을 수 있다.""")
    @ApiResponse(responseCode = "200", description = "재생성 성공 (더 다르게 못 만들었어도 200 — differentFromPrevious 로 알린다)")
    @ApiResponse(
            responseCode = "400",
            description = "지역 누락 · 일수 범위(1~3) 초과 · 좌표 범위 초과 · 필수값 누락"
                    + " · 첫날 연차 단위가 목록에 없는 값(FULL_DAY·HALF_DAY·QUARTER_DAY)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    @ApiResponse(responseCode = "404", description = "해당 지역으로 만들 수 있는 코스가 없음 (제외한 장소가 많아 볼거리가 남지 않은 경우 포함)")
    @ApiResponse(responseCode = "502", description = "관광정보(TourAPI) 조회 실패")
    ApiResponseBody<CourseRegenerateResponse> regenerate(CourseRegenerateRequest request);
}
