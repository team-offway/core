package com.offway.core.user.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 사용자·인증 관련 예외. */
public final class UserException extends BaseException {

    private UserException(ErrorCode errorCode) {
        super(errorCode);
    }

    private UserException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /** provider ID 토큰 검증 실패 — 서명·만료·issuer/audience 불일치. 원인은 로그·cause 로만 남긴다. */
    public static UserException invalidIdToken(Throwable cause) {
        return new UserException(UserErrorCode.INVALID_ID_TOKEN, cause);
    }

    /** 지원하지 않거나 서버에 audience 가 설정되지 않은 provider. */
    public static UserException unsupportedProvider() {
        return new UserException(UserErrorCode.UNSUPPORTED_PROVIDER);
    }

    /** refresh 토큰이 없거나 만료·폐기됨. */
    public static UserException invalidRefreshToken() {
        return new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }

    /** access 토큰이 없거나 무효·만료. */
    public static UserException invalidAccessToken() {
        return new UserException(UserErrorCode.INVALID_ACCESS_TOKEN);
    }

    /**
     * access 토큰 거절 — <b>원인을 달아서</b>(#41).
     *
     * <p>사유는 응답에 담지 않는다(어디까지 맞췄는지 알려줄 이유가 없다). 대신 cause 로 들고 다녀서 로그에
     * "만료됐다" 와 "서명이 안 맞는다" 를 가를 수 있게 한다 — 앞은 앱이 재발급하면 그만이고 뒤는 위조
     * 시도라 대응이 정반대다. 사유 없이 401 만 세면 그 둘이 한 덩어리로 섞인다.
     */
    public static UserException invalidAccessToken(Throwable cause) {
        return new UserException(UserErrorCode.INVALID_ACCESS_TOKEN, cause);
    }

    /** provider JWKS 조회 실패 — 외부 의존성 장애라 502. */
    public static UserException oidcProviderUnavailable(Throwable cause) {
        return new UserException(UserErrorCode.OIDC_PROVIDER_UNAVAILABLE, cause);
    }

    /** 토큰은 유효하지만 그 사용자가 없다 — 이미 탈퇴했다. */
    public static UserException withdrawnUser() {
        return new UserException(UserErrorCode.WITHDRAWN_USER);
    }
}
