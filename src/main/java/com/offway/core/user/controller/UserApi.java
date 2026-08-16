package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** 사용자 API 문서 계약. 매핑은 구현체({@link UserController})가 소유한다. */
@Tag(name = "사용자", description = "회원 탈퇴")
public interface UserApi {

    @Operation(
            summary = "회원 탈퇴",
            description =
                    """
                    로그인한 사용자의 계정과 데이터를 지운다. **되돌릴 수 없다** — 유예 기간이 없다.

                    **지우는 것**
                    - 계정 · 소셜 연결 · refresh 토큰
                    - 저장한 코스(일정·슬롯 포함) · 여행 후기 응답
                    - 연차 설정 · 연차 사용 내역

                    코스·연차는 아직 `X-Guest-Id` 로 묶여 있어(소유 키 전환 미완료) **이 헤더를 함께 보내야
                    지워진다.** 헤더가 없으면 계정만 지워지고 그 데이터는 남는다 — 앱은 저장·조회에 쓰던 것과
                    같은 값을 반드시 실어 보낸다.

                    **지우지 않는 것**
                    - 이미 발급한 **공유 링크**(`course_share`). 코스가 사라져 링크는 410(게시자가 삭제함)으로
                      답한다. 함께 지우면 404 가 되어 "링크를 잘못 옮겨 적었다" 와 구분되지 않는다.
                    - **소셜 계정 연결 해제**(카카오 unlink · Apple revoke)는 아직 하지 않는다. 우리 쪽 데이터는
                      지워지지만, provider 의 '연결된 서비스' 목록에는 남는다.

                    이미 발급된 access 토큰은 만료(기본 1시간)까지 서명 검증을 통과한다. 그 창에 다시 부르면
                    `USER-006` 이다.
                    """)
    @ApiResponse(responseCode = "200", description = "탈퇴 완료")
    @ApiResponse(
            responseCode = "401",
            description =
                    "access 토큰이 없거나 무효·만료(USER-004) · 자격증명 없음(COMMON-401) · 이미 탈퇴한 계정(USER-006)")
    ApiResponseBody<Void> withdraw(UUID userId);
}
