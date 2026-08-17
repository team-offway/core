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

    /** provider JWKS 조회 실패 — 외부 의존성 장애라 502. */
    public static UserException oidcProviderUnavailable(Throwable cause) {
        return new UserException(UserErrorCode.OIDC_PROVIDER_UNAVAILABLE, cause);
    }
}
