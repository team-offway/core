package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.RegionListResponse;
import com.offway.core.trip.domain.Category;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 지역 목록 API 문서 계약(#266). 매핑은 구현체({@link RegionListController})가 소유한다. */
@Tag(name = "지역 목록", description = "인구감소지역 89곳 페이지 조회")
public interface RegionListApi {

    @Operation(
            summary = "인구감소지역 목록",
            description = """
                    인구감소지역 89곳을 **방문자 랭킹 내림차순**으로 페이지에 담아 준다. 홈이 주는 상위 6곳
                    너머를 보는 "더보기" 화면이 쓴다. 카드 재료(한산도·볼거리 수·대표 이미지·카테고리)는
                    홈 카드와 같다.

                    **외부 API 를 부르지 않는다.** 방문자 집계·지역 콘텐츠·관광사진이 모두 적재된 값이라,
                    관광 API 한도가 소진되거나 포털이 점검 중이어도 목록은 그대로 나간다. 아직 콘텐츠가
                    적재되지 않은 지역은 목록에서 빠지지 않고 볼거리 0·이미지 없음으로 나간다.

                    **정렬 파라미터는 없다.** 정렬이 하나뿐이기 때문이다. 도달시간 순은 출발지 좌표가 있어야
                    정의되는데 이 엔드포인트는 그것을 받지 않는다 — 그쪽은 `POST /api/v1/regions/recommendations`
                    가 소유한다.

                    페이지 정보(`page`·`size`·`totalElements`·`totalPages`)는 응답 본문이 아니라 공통 래퍼의
                    `pageResponse` 에 실린다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공 (해당 카테고리에 지역이 없으면 빈 목록)")
    @ApiResponse(responseCode = "400", description = "category 가 정의되지 않은 값 (ALL·SIGHT·STAY·EXPERIENCE·FOOD 외)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<RegionListResponse> regions(
            @Parameter(description = "필터칩으로 좁히기. 생략하거나 ALL 이면 전체. 칩별 지역 수는 GET /api/v1/categories 가 준다")
                    Category category,
            @Parameter(description = "0부터 시작하는 페이지 번호. 기본 0. 음수는 0 으로 자른다") Integer page,
            @Parameter(description = "페이지 크기. 기본 20, 최대 100. 범위를 벗어나면 잘라 준다(거절하지 않는다)")
                    Integer size);
}
