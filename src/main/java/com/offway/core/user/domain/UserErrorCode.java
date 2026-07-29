package com.offway.core.user.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 사용자·인증 관련 에러 사유.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum UserErrorCode implements ErrorCode {

    /** provider ID 토큰 검증 실패(서명·만료·issuer/audience 불일치). 재로그인해야 풀린다. */
    INVALID_ID_TOKEN("USER-001", ErrorCategory.UNAUTHORIZED, "로그인 정보를 확인할 수 없습니다. 다시 로그인해 주세요."),

    /** 지원하지 않거나 서버에 설정되지 않은 provider. 클라이언트 입력이라 400. */
    UNSUPPORTED_PROVIDER("USER-002", ErrorCategory.BAD_REQUEST, "지원하지 않는 로그인 방식입니다."),

    /** refresh 토큰이 없거나 만료·폐기됨. 재로그인 유도. */
    INVALID_REFRESH_TOKEN("USER-003", ErrorCategory.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),

    /** access 토큰이 없거나 무효·만료. 클라이언트는 재발급을 시도해야 한다. */
    INVALID_ACCESS_TOKEN("USER-004", ErrorCategory.UNAUTHORIZED, "로그인이 필요합니다."),

    /**
     * provider 의 공개키(JWKS) 조회 실패. "네 토큰이 틀렸다(401)"와 구분해 502 로 내린다 — 전자는 재로그인,
     * 후자는 재시도로 클라이언트가 취할 행동이 다르다.
     */
    OIDC_PROVIDER_UNAVAILABLE("USER-005", ErrorCategory.EXTERNAL_API, "로그인 서비스에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    UserErrorCode(String code, ErrorCategory category, String message) {
        this.code = code;
        this.category = category;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String message() {
        return message;
    }
}
