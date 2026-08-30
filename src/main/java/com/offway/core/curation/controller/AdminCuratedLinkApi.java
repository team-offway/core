package com.offway.core.curation.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.curation.controller.dto.AdminCuratedLinkRequest;
import com.offway.core.curation.controller.dto.AdminCuratedLinkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

/**
 * 백오피스 — 큐레이션 링크 CRUD 문서 계약(#342). 매핑은 구현체({@link AdminCuratedLinkController})가 소유한다.
 *
 * <p><b>읽기까지 {@code ROLE_ADMIN} 을 요구한다.</b> 이 목록에는 아직 게시하지 않은 것과 기간이 지난 것이
 * 전부 들어 있어, 팀 밖에 보일 이유가 없다. 그래서 Basic 자격증명으로는 GET 도 403 이다.
 */
@Tag(name = "어드민 — 큐레이션 링크", description = "앱 4개 면에 내리는 외부 링크를 배포 없이 관리한다")
public interface AdminCuratedLinkApi {

    @Operation(
            summary = "큐레이션 링크 목록",
            description =
                    """
                    **게시 여부·기간과 무관하게 전부** 준다. 만들다 만 것과 지난 것을 못 보면 고칠 수가 없다.

                    정렬은 앱과 같다(`displayOrder` 오름차순, 같으면 `id`). 화면에서 보는 순서와 실제 노출
                    순서가 같아야 어드민이 정렬값을 감으로 맞추지 않는다.

                    페이지 메타는 응답 래퍼의 `pageResponse` 로 나간다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 배열)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님 — 일반 사용자 토큰이거나 Basic 자격증명")
    ApiResponseBody<List<AdminCuratedLinkResponse>> list(
            @Parameter(description = "0부터. 음수는 0으로 자른다", example = "0") Integer page,
            @Parameter(description = "기본 20 · 최대 100. 넘으면 100으로 자른다", example = "20") Integer size);

    @Operation(summary = "큐레이션 링크 단건")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님")
    @ApiResponse(responseCode = "404", description = "없는 링크(CURATION-006)")
    ApiResponseBody<AdminCuratedLinkResponse> get(
            @Parameter(description = "링크 ID", example = "3") long id);

    @Operation(
            summary = "큐레이션 링크 생성",
            description =
                    """
                    **`published` 를 켜지 않으면 앱에 안 나간다.** 만들다 만 항목이 곧바로 사용자에게 보이지
                    않게 하려는 것이라, 켜는 것은 명시적 행위여야 한다.

                    `alwaysOn` 이 거짓이면 `endsOn` 이 **필수**다. 날짜를 비운 것이 "상시" 인지 "깜빡한 것"
                    인지 값만 보고 알 수 없어서 생긴 규칙이다 — 예전에 사업이 끝난 혜택 뱃지가 영영 남았다(#217).
                    """)
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(
            responseCode = "400",
            description = "필수값 누락·길이 초과 · https 아닌 주소(CURATION-001) · 상시가 아닌데 종료일 없음"
                    + "(CURATION-002) · 종료일이 시작일보다 앞(CURATION-003) · 칩 문구 길이 초과(CURATION-004)"
                    + " · 노출 화면 없음(CURATION-005)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님")
    ApiResponseBody<AdminCuratedLinkResponse> create(UUID adminUserId, AdminCuratedLinkRequest request);

    @Operation(
            summary = "큐레이션 링크 수정",
            description =
                    """
                    **부분 수정이 아니라 전체 교체**다. 보낸 필드만 바꾸지 않고 전부 덮어쓴다 — 기간 규칙처럼
                    여러 필드가 함께 봐야 성립하는 불변식이 있어, 한 필드만 바꾸면 나머지와 어긋난 상태가
                    만들어진다.

                    검증은 생성과 같은 코드를 탄다. 만들 때는 막히고 고칠 때는 통과하는 값이 있으면 저장
                    한 번으로 규칙을 우회하게 된다.
                    """)
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "생성과 같은 검증 실패")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님")
    @ApiResponse(responseCode = "404", description = "없는 링크(CURATION-006)")
    ApiResponseBody<AdminCuratedLinkResponse> update(
            UUID adminUserId,
            @Parameter(description = "링크 ID", example = "3") long id,
            AdminCuratedLinkRequest request);

    @Operation(
            summary = "큐레이션 링크 삭제",
            description =
                    """
                    없는 것을 지우라는 요청은 **404** 다. 어드민 화면은 목록을 들고 있어서, 다른 탭에서 이미
                    지운 항목을 누르면 여기 닿는다 — 조용히 성공시키면 화면이 낡은 목록을 그대로 믿는다.

                    204 를 쓰지 않는다(응답 래퍼가 항상 body 를 만든다). 200 + `data: null` 이다.
                    """)
    @ApiResponse(responseCode = "200", description = "삭제 성공 (data 는 null)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님")
    @ApiResponse(responseCode = "404", description = "없는 링크(CURATION-006)")
    ApiResponseBody<Void> delete(
            UUID adminUserId, @Parameter(description = "링크 ID", example = "3") long id);
}
