package com.offway.core.common.external;

/**
 * 응답 코드 하나가 <b>외부 시스템의 상태</b>에 대해 무엇을 말해주는가(#489).
 *
 * <p>판정 기준을 여기 한 곳에 둔다. 전에는 같은 판단이 {@code ExternalHealthFilter}(알림)와
 * {@code ProbeResult}(어드민 표) 두 곳에 흩어져 있었는데, 그러면 <b>표와 알림이 서로 다른 말을 한다.</b>
 *
 * <h2>4xx 를 한 덩어리로 묶으면 안 된다</h2>
 *
 * <p>2026-09-07 운영 키가 다섯 서비스에서 {@code 403 "승인 반영 대기"} 를 받고 있었다. 사용자 화면에서
 * 장소 소개·사진·공휴일·혼잡도가 통째로 안 나오는 상태였는데 <b>알림이 한 줄도 안 갔다.</b> 4xx 를
 * 전부 "우리 요청이 잘못된 것" 으로 보고 실패에서 뺐기 때문이다.
 *
 * <p>그 판단은 절반만 맞았다. 갈라야 하는 것은 <b>이 요청 하나의 문제인가, 그 API 전체가 막힌 것인가</b> 다.
 *
 * <ul>
 *   <li>{@code 400}·{@code 404} — 이 요청만의 문제다. 없는 장소를 물으면 404 가 오는 것이 정상이고,
 *       그걸로 알리면 신호가 죽는다. <b>실패로 세지 않는다.</b>
 *   <li>{@code 401}·{@code 403}·{@code 429} — 키가 틀렸거나, 승인이 풀렸거나, 한도를 태웠다.
 *       <b>다음 요청도 똑같이 막힌다.</b> 사용자 입장에서는 외부가 죽은 것과 결과가 같다.
 * </ul>
 *
 * <h2>사유를 구분해 알린다</h2>
 *
 * <p>"키가 막힘" 과 "외부가 죽음" 은 사람이 할 일이 다르다. 전자는 포털에서 활용신청을 다시 봐야 하고,
 * 후자는 기다리는 것 말고 할 게 없다. 알림 문구가 그 둘을 섞으면 받는 사람이 매번 확인하러 가야 한다.
 */
public enum ExternalHttpOutcome {

    /** 정상. */
    OK,

    /** 우리 요청 하나가 잘못됐다 — 외부 상태에 대해서는 아무 말도 안 한다. */
    BAD_REQUEST,

    /** 이 API 가 우리에게 막혔다 — 자격·한도 문제라 다음 요청도 같다. */
    BLOCKED,

    /** 외부가 스스로 못 하겠다고 답했다. */
    SERVER_ERROR;

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int CLIENT_ERROR_FROM = 400;
    private static final int SERVER_ERROR_FROM = 500;

    public static ExternalHttpOutcome of(int httpStatus) {
        if (isBlocked(httpStatus)) {
            return BLOCKED;
        }
        if (httpStatus >= SERVER_ERROR_FROM) {
            return SERVER_ERROR;
        }
        if (httpStatus >= CLIENT_ERROR_FROM) {
            return BAD_REQUEST;
        }
        return OK;
    }

    private static boolean isBlocked(int httpStatus) {
        return httpStatus == UNAUTHORIZED || httpStatus == FORBIDDEN || httpStatus == TOO_MANY_REQUESTS;
    }

    /** 이 결과를 시스템 장애로 셀 것인가. */
    public boolean isFailure() {
        return this == BLOCKED || this == SERVER_ERROR;
    }

    /**
     * 그 자리에서 다시 물어볼 값어치가 있나.
     *
     * <p>확인 호출은 <b>"일시적인가 진짜 죽었나"</b> 를 가르려는 것이다. 4xx 는 가를 것이 없다 —
     * 요청이 잘못됐거나 자격이 막혔거나, 몇 번을 더 물어도 같은 답이 온다.
     *
     * <p><b>막힌 것도 다시 묻지는 않지만 알림은 간다.</b> 두 질문을 섞지 않는다({@link #isFailure()}).
     *
     * <p>{@link #OK} 가 여기 포함되는 이유 — 이 판정을 쓰는 프로브의 상태 코드는 순수 HTTP 코드가 아니다.
     * 응답이 아예 없으면 {@code 0} 이고, 외부가 {@code 200} 에 실패를 실어 보내는 경우도 있다(#479).
     * 둘 다 코드만으로는 사유를 못 가르므로 다시 물어보는 편이 낫다.
     */
    public boolean worthRetrying() {
        return this != BAD_REQUEST && this != BLOCKED;
    }

    /** 알림에 실을 사유. 받는 사람이 할 일이 갈리므로 문구를 나눈다. */
    public String describe(int httpStatus) {
        return this == BLOCKED
                ? "HTTP %d — 키가 막혔습니다(활용신청·한도 확인)".formatted(httpStatus)
                : "HTTP %d".formatted(httpStatus);
    }
}
