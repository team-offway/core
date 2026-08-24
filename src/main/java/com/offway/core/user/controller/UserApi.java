package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.controller.dto.MyUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** 사용자 API 문서 계약. 매핑은 구현체({@link UserController})가 소유한다. */
@Tag(name = "사용자", description = "내 정보 조회 · 회원 탈퇴")
public interface UserApi {

    @Operation(
            summary = "내 정보 조회",
            description =
                    """
                    로그인한 사용자 자신의 정보를 준다. 앱이 재시작·재설치 후에도 마이페이지를 서버 값으로 채우게
                    하려는 것이다 — 로그인 응답을 로컬에 저장해 두는 것 말고 알 방법이 없었다.

                    **null 로 올 수 있는 필드**
                    - `email` — 카카오는 동의를 안 하면 주지 않고, Apple 은 **최초 로그인에만** 준다.
                      그때 앱이 `email` 을 로그인 요청에 실어 보내지 않았다면 서버도 모른다.
                    - `provider` — local 개발 로그인(`/auth/dev-login`)으로 만든 계정은 연결이 없다.
                      운영에서는 항상 값이 있다.
                    - `profileImageUrl` — **Apple 은 사진을 아예 주지 않는다.** 카카오는 동의를 거부하거나
                      기본 이미지를 쓰면 없다. 없으면 앱이 기본 아이콘을 그린다.

                    `profileImageUrl` 은 **로그인할 때마다 provider 값으로 갱신**한다. 사진을 바꾸거나
                    카카오 CDN 주소가 만료돼도 다음 로그인에 따라온다. 주소가 죽어 있으면 앱이 기본 아이콘으로
                    되돌아가면 된다.

                    `nickname` 은 항상 있다. provider 가 이름을 주지 않았으면 가입 때 기본값이 채워진다.

                    **`isNewUser` 는 여기 없다.** 그건 "이번 로그인이 가입이었나" 라서 조회에는 뜻이 없다 —
                    온보딩 분기는 로그인 응답(`POST /auth/callback/{provider}`)이 소유한다. 앱은 그 값을 받은
                    시점에 온보딩 완료 여부를 로컬에 남겨야 한다.

                    이미 발급한 access 토큰은 탈퇴 후에도 만료(기본 1시간)까지 서명 검증을 통과한다. 그 창에
                    이 API 를 부르면 `USER-006` 이다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(
            responseCode = "401",
            description =
                    "access 토큰이 없거나 무효·만료(USER-004) · 자격증명 없음(COMMON-401) · 이미 탈퇴한 계정(USER-006)")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<MyUserResponse> me(UUID userId);

    @Operation(
            summary = "회원 탈퇴",
            description =
                    """
                    로그인한 사용자의 계정과 데이터를 지운다. **되돌릴 수 없다** — 유예 기간이 없다.

                    **지우는 것**
                    - 계정 · **우리 쪽 소셜 연결 기록**(`user_identity`) · refresh 토큰
                      → 같은 소셜 계정으로 다시 가입하면 **새 사용자**가 된다
                    - 저장한 코스(일정·슬롯 포함) · 여행 후기 응답
                    - 연차 설정 · 연차 사용 내역
                    - 받은 알림

                    **지울 대상은 access 토큰이 정한다.** 요청 헤더·본문에 무엇을 실어도 대상이 바뀌지 않는다 —
                    코스·연차·후기·알림이 전부 `user_id` 로 묶여 있어, 인증으로 확인된 그 사용자의 것만 지워진다.

                    **지우지 않는 것**
                    - 이미 발급한 **공유 링크**(`course_share`). 코스가 사라져 링크는 410(게시자가 삭제함)으로
                      답한다. 함께 지우면 404 가 되어 "링크를 잘못 옮겨 적었다" 와 구분되지 않는다.
                    - **카카오 '연결된 서비스'.** 위에서 지우는 `user_identity` 는 우리 DB 의 기록이고, 이건
                      카카오가 들고 있는 것이라 별개다. 끊으려면 Admin 키가 필요한데 아직 없다. Apple 이
                      문서화한 대안(데이터 삭제 + 사용자가 직접 해제하도록 안내)이 카카오에도 그대로 적용된다.

                    **Apple 은 지운다**(#287). `POST /auth/revoke` 로 '이 App으로 로그인' 목록에서 없앤다.
                    그러려면 로그인 때 앱이 `authorizationCode` 를 함께 보내야 한다 — 그 코드는 **1회용·5분**이라
                    탈퇴 시점에는 이미 없고, 로그인 그 순간에 refresh 토큰으로 바꿔 둬야 한다.

                    안 끊기는 경우가 있고, 그때도 **탈퇴는 성공한다**.
                    - 이 기능 이전에 로그인한 사용자 — 저장된 토큰이 없다. 소급해서 채울 수 없다(재로그인하면 채워진다)
                    - 앱이 `authorizationCode` 를 안 보낸 경우
                    - 서버에 `.p8` 자격이 없거나 Apple 호출이 실패한 경우

                    셋 다 우리 데이터는 지워지고 Apple 목록에만 남는다. 사용자가 Apple 설정에서 직접 지울 수
                    있고, 서버는 왜 못 끊었는지 로그에 남긴다.

                    이미 발급된 access 토큰은 만료(기본 1시간)까지 서명 검증을 통과한다. 그 창에 다시 부르면
                    `USER-006` 이다.
                    """)
    @ApiResponse(responseCode = "200", description = "탈퇴 완료")
    @ApiResponse(
            responseCode = "401",
            description =
                    "access 토큰이 없거나 무효·만료(USER-004) · 자격증명 없음(COMMON-401) · 이미 탈퇴한 계정(USER-006)")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<Void> withdraw(UUID userId);
}
