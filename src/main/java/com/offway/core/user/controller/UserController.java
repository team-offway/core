package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.config.LoginUser;
import com.offway.core.user.controller.dto.MyUserResponse;
import com.offway.core.user.service.MyUserService;
import com.offway.core.user.service.UserWithdrawalService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private static final String WITHDRAWN_DETAIL = "탈퇴 처리되었습니다.";

    private final UserWithdrawalService userWithdrawalService;
    private final MyUserService myUserService;

    /** 내 정보 — 앱이 재시작·재설치 후에도 마이페이지를 서버 값으로 채운다(#282). */
    @Override
    @GetMapping("/me")
    public ApiResponseBody<MyUserResponse> me(@LoginUser UUID userId) {
        return ApiResponseBody.ok(MyUserResponse.from(myUserService.myUser(userId)));
    }

    /**
     * 지울 대상이 남지 않아 내려보낼 데이터가 없지만 204 가 아니라 200 이다 — 공통 래퍼가 항상 body 를 만들어
     * "body 없음" 이 본질인 204 와 충돌한다(exception-and-response 규약).
     *
     * <p><b>지울 대상은 access 토큰이 정한다</b>(#280). 요청이 대상을 정하면 남의 값을 적어 남의 데이터를
     * 지울 수 있다 — 실제로 그 공격이 재현됐고, 소유 키를 {@code user_id} 로 옮겨 닫았다.
     *
     * <p><b>못 지우는 경우가 없다.</b> 예전에는 코스·연차가 게스트 키로 묶여 있어, 로그인할 때 기기를 이어 두지
     * 못했으면 계정만 지워지고 나머지는 주인 없이 남았다. 이제 코스·연차·후기·알림이 전부 탈퇴하는 본인에게
     * 묶여 있어 한 번에 닿는다.
     */
    @Override
    @DeleteMapping("/me")
    public ApiResponseBody<Void> withdraw(@LoginUser UUID userId) {
        userWithdrawalService.withdraw(userId);
        return ApiResponseBody.okWithDetail(WITHDRAWN_DETAIL);
    }
}
