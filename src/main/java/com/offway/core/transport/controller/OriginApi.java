package com.offway.core.transport.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.transport.controller.dto.OriginSuggestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * 출발지 자동완성 문서 계약(#590). 매핑은 구현체({@link OriginController})가 소유한다.
 *
 * <p><b>왜 이 API 가 생겼나.</b> 앱이 GPS 수집을 그만둔다 — 위치정보 사전상담에서 "개인의 위치를
 * 특정하지 않더라도 수집하는 순간 신고 대상" 이라는 답을 받았다. 그래서 사용자가 출발지를 직접 고른다.
 */
@Tag(name = "출발지", description = "코스의 출발지를 검색해 고른다 — 앱은 좌표를 다루지 않는다")
public interface OriginApi {

    @Operation(
            summary = "출발지 자동완성",
            description =
                    """
                    검색어에 걸리는 역·터미널과 주소를 순위대로 준다.

                    **무엇이 걸리나**

                    - **지역** — `서울` 을 치면 서울에 있는 역·터미널이 걸린다. 이름에 "서울" 이 없는
                      용산·청량리·영등포·왕십리·수서도 포함된다
                    - **이름** — `청량리`·`동서울` 처럼 허브 이름으로 찾는다
                    - **부르는 이름** — `고속버스터미널`·`강남터미널` 로 서울 고속버스터미널이 걸린다
                    - **주소·장소** — 우리 허브에 없는 곳(`분당`·`해운대`)은 주소 검색으로 메운다.
                      허브로 목록이 이미 찬 검색어에서는 외부를 부르지 않는다

                    **정렬** — ① 이름에 걸린 것 ② 부르는 이름이 있는 대표 허브 ③ 같은 지역의 나머지
                    ④ 주소·장소. `서울` 을 치면 서울역·고속버스터미널·동서울터미널이 먼저 온다.

                    **무엇이 빠지나** — 좌표가 없는 허브(동선에 못 올린다)와 경유 정류소(특정 노선만
                    서므로 "거기서 타세요" 가 틀린 안내가 된다). 같은 지점에 코드가 여럿인 곳은 한 줄로 접는다.

                    **두 글자 미만이면 빈 배열이다.** 한 글자는 `서` 하나에 서울·서산·서천이 전부 걸려
                    목록이 뜻을 잃는다.

                    받은 `code` 를 코스 생성·재생성·지역 추천의 `originCode` 에 그대로 넣으면 된다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공 (걸리는 것이 없거나 검색어가 짧으면 빈 배열)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    ApiResponseBody<List<OriginSuggestionResponse>> suggest(
            @Parameter(
                            description = "사용자가 친 말. 두 글자 이상이어야 검색한다(공백·구두점은 무시)",
                            example = "서울")
                    String query);
}
