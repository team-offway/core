package com.offway.core.user.domain;

import com.offway.core.common.exception.ErrorCategory;

/**
 * 웹 로그인이 실패한 사유(#343) — <b>화면이 사람에게 설명할 수 있는 만큼</b>으로 나눈다.
 *
 * <h2>왜 예외가 아니라 이 enum 인가</h2>
 *
 * <p>브라우저가 <b>주소창을 이동하는 중</b>이라 JSON 401 을 내리면 화면이 그냥 죽는다. 사람은 원시 JSON 을
 * 마주하고 무엇을 해야 할지 알 수 없다. 그래서 실패도 백오피스 화면으로 되돌려 보내고, 무엇이 잘못됐는지는
 * 이 값으로 전한다.
 *
 * <p>API 경로가 아니라 <b>사람이 보는 경로</b>라 가능한 선택이다. 앱이 부르는 경로였다면 code 로 답해야 한다.
 *
 * <h2>왜 사유를 나누나</h2>
 *
 * <p>사람이 <b>다음에 할 일이 다르기</b> 때문이다. 동의를 취소한 것은 다시 누르면 되고, 카카오가 안 뜨는
 * 것은 기다려야 하고, 설정이 빠진 것은 배포가 필요하다. 하나로 뭉쳐 "로그인 실패" 만 띄우면 셋 다 같은
 * 화면을 보고 같은 행동(다시 누르기)을 반복한다.
 */
public enum WebLoginFailure {

    /** 카카오 로그인 설정(REST API 키·콜백 주소)이 없다 — 배포가 필요하다. */
    NOT_CONFIGURED("not_configured"),

    /** 사용자가 카카오 동의 화면에서 취소했다. 실패가 아니라 선택이라 조용히 되돌린다. */
    DENIED("denied"),

    /**
     * 이 콜백이 우리가 시작한 로그인이 아니다({@link OAuthState} 불일치).
     *
     * <p>쿠키가 만료됐을 수도 있고 공격일 수도 있다. <b>둘을 구분하지 않는다</b> — 어느 쪽이든 할 일은
     * 처음부터 다시 시작하는 것 하나뿐이고, 구분해서 알려주면 공격자에게 힌트가 된다.
     */
    INVALID_STATE("invalid_state"),

    /** 카카오가 코드나 토큰을 거절했다 — 만료된 코드, 이미 쓴 코드, 설정 불일치. */
    REJECTED("rejected"),

    /** 카카오를 부르지 못했다 — 타임아웃·5xx. 잠시 뒤 다시 하면 된다. */
    UNAVAILABLE("unavailable");

    private final String code;

    WebLoginFailure(String code) {
        this.code = code;
    }

    /** 화면으로 되돌려 보낼 때 쓰는 값. 사람이 읽는 문구는 화면이 소유한다. */
    public String code() {
        return code;
    }

    /**
     * 로그인 도중 터진 도메인 예외를 사유로 옮긴다.
     *
     * <p>외부 호출 실패({@link ErrorCategory#EXTERNAL_API})만 갈라낸다. 그것만 <b>기다리면 풀리는</b>
     * 실패이고, 나머지(토큰 거절·지원하지 않는 provider)는 다시 눌러도 같은 결과라 구분할 실익이 없다.
     */
    public static WebLoginFailure of(ErrorCategory category) {
        return category == ErrorCategory.EXTERNAL_API ? UNAVAILABLE : REJECTED;
    }
}
