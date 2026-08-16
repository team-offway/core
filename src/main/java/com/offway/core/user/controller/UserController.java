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

    /** 코스·연차의 소유 키. 인증이 붙었지만 그 데이터는 아직 이 값으로 묶여 있다. */
    private static final String GUEST_HEADER = "X-Guest-Id";

    private static final String WITHDRAWN_DETAIL = "탈퇴 처리되었습니다.";

    private final UserWithdrawalService userWithdrawalService;

    /**
     * 지울 대상이 남지 않아 내려보낼 데이터가 없지만 204 가 아니라 200 이다 — 공통 래퍼가 항상 body 를 만들어
     * "body 없음" 이 본질인 204 와 충돌한다(exception-and-response 규약).
     *
     * <p>{@code X-Guest-Id} 는 <b>선택</b>이다. 없다고 탈퇴 자체를 400 으로 막으면, 계정을 지울 권리가 헤더
     * 하나에 인질로 잡힌다 — 심사·방침이 요구하는 것은 계정 삭제다. 대신 그때 무엇을 못 지웠는지 로그로 남긴다.
     */
    @Override
    @DeleteMapping("/me")
    public ApiResponseBody<Void> withdraw(@LoginUser UUID userId) {
        userWithdrawalService.withdraw(userId);
        return ApiResponseBody.okWithDetail(WITHDRAWN_DETAIL);
    }
}
