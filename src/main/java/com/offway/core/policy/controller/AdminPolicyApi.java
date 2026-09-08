package com.offway.core.policy.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.policy.controller.dto.AdminPolicyRequest;
import com.offway.core.policy.controller.dto.AdminPolicyResponse;
import com.offway.core.policy.controller.dto.AdminPolicyScopeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

/**
 * 백오피스 — 혜택 CRUD 문서 계약(#344). 매핑은 구현체({@link AdminPolicyController})가 소유한다.
 *
 * <p><b>여기가 seed SQL 을 대신한다.</b> 정책은 지금까지 {@code R__seed_policies.sql} 이 소유했고, 값을
 * 하나 고치려면 배포가 필요했다. 그 파일은 묘비만 남기고 비웠다.
 *
 * <p><b>읽기까지 {@code ROLE_ADMIN} 을 요구한다.</b> 이 목록에는 미검증 정책과 기간이 지난 것이 전부
 * 들어 있어, 팀 밖에 보일 이유가 없다.
 *
 * <h2>대상 지역은 여기서 못 고친다</h2>
 *
 * <p>어느 지역에 뜨는지는 {@code PolicyType} 이 정한 태그와 {@code region_tag} 가 잇고, 참여 지자체
 * 명단은 {@code R__seed_program_region_tags.sql} 이 소유한다. 그쪽은 이 작업의 범위 밖이다.
 */
@Tag(name = "어드민 — 정책", description = "여행 혜택을 배포 없이 관리한다")
public interface AdminPolicyApi {

    @Operation(
            summary = "정책 목록",
            description =
                    """
                    **검증 여부·기간과 무관하게 전부** 준다. 미검증으로 방치된 것과 지난 것을 못 보면
                    고칠 수가 없다.

                    페이지 메타는 응답 래퍼의 `pageResponse` 로 나간다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 배열)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님 — 일반 사용자 토큰이거나 Basic 자격증명")
    ApiResponseBody<List<AdminPolicyResponse>> list(
            @Parameter(description = "0부터. 음수는 0으로 자른다", example = "0") Integer page,
            @Parameter(description = "기본 20 · 최대 100. 넘으면 100으로 자른다", example = "20") Integer size);

    @Operation(summary = "정책 단건")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님")
    @ApiResponse(responseCode = "404", description = "없는 정책(POLICY-001)")
    ApiResponseBody<AdminPolicyResponse> get(@Parameter(description = "정책 ID", example = "2") long id);

    @Operation(
            summary = "정책 생성",
            description =
                    """
                    **`verified` 를 켜지 않으면 앱에 안 나간다.** 확인이 안 끝난 정책이 뱃지로 나가면
                    사용자가 받을 수 없는 혜택을 보러 갑니다 — 켜는 것은 명시적 행위여야 한다.

                    **`type` 은 7개 중 선택이고 자유 입력이 아니다.** 뱃지 문구와 대상 지역이 여기 묶여
                    있어, 분류를 고르면 그 둘이 함께 정해진다.

                    **`periodEnd` 를 비우면 끝나지 않는다.** 사업이 끝나도 뱃지가 남으므로, 지자체별로
                    기간이 다르면 바깥 경계를 넣고 사정은 `periodNote` 로 말한다(#217).
                    """)
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(
            responseCode = "400",
            description = "필수값 누락·길이 초과 · https 아닌 신청 주소(POLICY-002) · 시작일이 종료일보다 늦음(POLICY-003)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님")
    @ApiResponse(
            responseCode = "409",
            description = "같은 분류가 이미 그 기간에 노출됨(POLICY-004) — 뱃지가 두 개 뜬다 · 같은 분류를 다른 관리자가 저장 중(POLICY-005)")
    ApiResponseBody<AdminPolicyResponse> create(UUID adminUserId, AdminPolicyRequest request);

    @Operation(
            summary = "정책 수정",
            description =
                    """
                    **부분 수정이 아니라 전체 교체**다. 검증은 생성과 같은 코드를 탄다 — 만들 때는 막히고
                    고칠 때는 통과하는 값이 있으면 저장 한 번으로 규칙을 우회하게 된다.

                    **`type` 도 바꿀 수 있다.** 잘못 고른 분류를 고치려면 필요하다. 다만 바꾸면 뱃지 문구와
                    대상 지역이 통째로 달라진다.
                    """)
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "생성과 같은 검증 실패")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님")
    @ApiResponse(responseCode = "404", description = "없는 정책(POLICY-001)")
    @ApiResponse(responseCode = "409",
            description = "같은 분류가 이미 그 기간에 노출됨(POLICY-004) · 같은 분류를 다른 관리자가 저장 중(POLICY-005)")
    ApiResponseBody<AdminPolicyResponse> update(
            UUID adminUserId, @Parameter(description = "정책 ID", example = "2") long id, AdminPolicyRequest request);

    @Operation(
            summary = "정책 삭제",
            description =
                    """
                    없는 것을 지우라는 요청은 **404** 다. 화면이 낡은 목록을 그대로 믿지 않게 하려는 것이다.

                    204 를 쓰지 않는다(응답 래퍼가 항상 body 를 만든다). 200 + `data: null` 이다.
                    """)
    @ApiResponse(responseCode = "200", description = "삭제 성공 (data 는 null)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님")
    @ApiResponse(responseCode = "404", description = "없는 정책(POLICY-001)")
    ApiResponseBody<Void> delete(UUID adminUserId, @Parameter(description = "정책 ID", example = "2") long id);

    @Operation(
            summary = "분류별 대상 지역",
            description = """
                    분류를 고르면 **어느 지역에 뜨는지**를 곳 수와 지역 목록으로 돌려준다.

                    분류마다 대상이 크게 갈린다 — 숙박세일페스타 85곳, 반값여행 25곳, 나머지 다섯은
                    89곳 전부다. 분류를 잘못 고르면 85곳짜리가 25곳짜리로 조용히 줄어든다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "로그인하지 않았거나 토큰이 만료됨")
    @ApiResponse(responseCode = "403", description = "어드민 권한이 없음")
    ApiResponseBody<List<AdminPolicyScopeResponse>> scopes();
}
