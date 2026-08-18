package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.config.LoginUser;
import com.offway.core.user.service.UserWithdrawalService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private static final String WITHDRAWN_DETAIL = "탈퇴 처리되었습니다.";

    private final UserWithdrawalService userWithdrawalService;

    /**
     * 지울 대상이 남지 않아 내려보낼 데이터가 없지만 204 가 아니라 200 이다 — 공통 래퍼가 항상 body 를 만들어
     * "body 없음" 이 본질인 204 와 충돌한다(exception-and-response 규약).
     *
     * <p><b>게스트 헤더를 받지 않는다.</b> 지울 대상은 로그인할 때 기록해 둔 기기 연결이 정한다 — 요청이
     * 대상을 정하면 남의 값을 적어 남의 데이터를 지울 수 있다.
     *
     * <p>이어 둔 기기가 없으면 계정만 지워진다. 그렇다고 탈퇴를 막지는 않는다 — 계정을 지울 권리가 옛 로그인에
     * 인질로 잡힌다. 심사·방침이 요구하는 것은 계정 삭제다. 대신 무엇을 못 지웠는지 로그로 남긴다.
     */
    @Override
    @DeleteMapping("/me")
    public ApiResponseBody<Void> withdraw(@LoginUser UUID userId) {
        userWithdrawalService.withdraw(userId);
        return ApiResponseBody.okWithDetail(WITHDRAWN_DETAIL);
    }
}
