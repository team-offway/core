package com.offway.core.curation.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 큐레이션 링크 관련 에러 사유(#341).
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 *
 * <p>지금 이 사유들에 닿는 것은 seed SQL 로 넣다 도메인 검증에 걸리는 경우뿐이다. 어드민 CRUD(#342)가
 * 붙으면 그때부터 <b>사람이 폼에서 만드는 400</b> 이 되므로, message 는 처음부터 사용자 대면 문구로 둔다.
 */
public enum CurationErrorCode implements ErrorCode {

    /** 링크 주소가 https 가 아니거나 host 가 없음 — 웹뷰가 임의 스킴을 열거나, 눌러도 아무 데도 못 간다. */
    INSECURE_LINK_URL("CURATION-001", ErrorCategory.BAD_REQUEST, "링크 주소가 올바르지 않습니다. https 주소를 넣어 주세요."),

    /** 상시 노출이 아닌데 종료일이 없음 — 비워 두면 영구 노출로 굳는다. */
    END_DATE_REQUIRED("CURATION-002", ErrorCategory.BAD_REQUEST, "상시 노출이 아니면 종료일을 지정해야 합니다."),

    /** 종료일이 시작일보다 앞. */
    PERIOD_REVERSED("CURATION-003", ErrorCategory.BAD_REQUEST, "종료일은 시작일보다 앞설 수 없습니다."),

    /** 칩 문구가 화면이 감당하는 길이를 넘음. */
    CHIP_TEXT_TOO_LONG("CURATION-004", ErrorCategory.BAD_REQUEST, "칩 문구가 너무 깁니다."),

    /** 내릴 화면을 하나도 고르지 않음 — 아무 데도 안 나가는 항목이 만들어진다. */
    SURFACE_REQUIRED("CURATION-005", ErrorCategory.BAD_REQUEST, "노출할 화면을 하나 이상 골라야 합니다."),

    /** 어드민이 없는 항목을 열거나 고치려 함(#342). 이미 지운 것을 다른 탭에서 누르면 여기 닿는다. */
    LINK_NOT_FOUND("CURATION-006", ErrorCategory.NOT_FOUND, "요청한 큐레이션 링크를 찾을 수 없습니다."),

    /** 썸네일로 허용하지 않는 종류(#377). SVG 처럼 스크립트를 품는 형식이 우리 도메인에서 열리면 안 된다. */
    UNSUPPORTED_IMAGE_TYPE(
            "CURATION-007", ErrorCategory.BAD_REQUEST, "JPG · PNG · WEBP 이미지만 올릴 수 있습니다."),

    /** 썸네일 크기가 상한을 넘음(#377). */
    IMAGE_TOO_LARGE("CURATION-008", ErrorCategory.BAD_REQUEST, "이미지가 너무 큽니다. 5MB 이하로 올려 주세요."),

    /**
     * 저장소 자격증명이 없어 업로드 주소를 낼 수 없음(#377).
     *
     * <p>사용자 잘못이 아니라 <b>배포 설정</b>이 빠진 상태다. 그래도 500 으로 두지 않는다 — 코드 버그가
     * 아니라 고칠 곳이 환경변수라, 일반 500 에 섞이면 로그에서 그 구분이 사라진다.
     */
    IMAGE_STORAGE_UNAVAILABLE(
            "CURATION-009", ErrorCategory.EXTERNAL_API, "이미지 저장소를 사용할 수 없습니다. 주소 붙여넣기로 등록해 주세요.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    CurationErrorCode(String code, ErrorCategory category, String message) {
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
