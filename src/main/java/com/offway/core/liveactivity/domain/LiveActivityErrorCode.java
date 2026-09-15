package com.offway.core.liveactivity.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * Live Activity 토큰 관련 에러 사유(#575).
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum LiveActivityErrorCode implements ErrorCode {

    /**
     * 푸시 토큰이 비었거나 너무 길다.
     *
     * <p><b>메시지에 토큰을 담지 않는다.</b> detail 은 그대로 응답에 나가고 로그에도 남는데, 이 값을
     * 아는 쪽은 그 사람의 잠금화면에 내용을 그릴 수 있다(로깅 규약).
     */
    INVALID_PUSH_TOKEN("LIVE-001", ErrorCategory.BAD_REQUEST, "푸시 토큰이 올바르지 않습니다."),

    /** 코스 식별자가 없거나 0 이하다. */
    INVALID_COURSE_ID("LIVE-002", ErrorCategory.BAD_REQUEST, "코스 식별자가 올바르지 않습니다."),

    /**
     * 그 코스가 없거나 <b>내 것이 아니다</b>.
     *
     * <p>둘을 가르지 않는다. 가르면 "있는데 남의 것" 과 "아예 없다" 가 응답으로 구분돼, 코스 id 를
     * 훑는 것만으로 남의 코스가 존재하는지 알아낼 수 있다.
     *
     * <p><b>등록 때 주인을 확인해야 하는 이유</b>: 이 등록의 대가로 서버가 그 코스의 여행지·날짜를
     * 매일 잠금화면에 그려 준다. 확인을 빠뜨리면 남의 코스 id 를 적는 것만으로 그 사람의 여행 일정을
     * 받아 볼 수 있다.
     */
    COURSE_NOT_FOUND("LIVE-003", ErrorCategory.NOT_FOUND, "코스를 찾을 수 없습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    LiveActivityErrorCode(String code, ErrorCategory category, String message) {
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
