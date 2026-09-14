package com.offway.core.common.external;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * data.go.kr 게이트웨이가 내는 사유를 로그용 한 줄로(#569).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>인증키가 막히거나 한도가 마르면 <b>{@code response} 가 아예 없는 다른 envelope</b>이 온다.
 * 실측(2026-09-14, 집중률·고캠핑·반려동반 셋 모두 같은 모양):
 *
 * <pre>{@code
 * {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
 *    "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
 *    "returnAuthMsg":"등록되지 않은 서비스키",
 *    "returnReasonCode":"30"}}}
 * }</pre>
 *
 * <p>이때 {@code resultCode} 는 빈 문자열로 읽힌다. 그것만 로그에 남기면 <b>무엇이 막혔는지 모른다</b> —
 * 키가 만료된 것인지 한도가 마른 것인지 구분이 안 된다.
 *
 * <h2>왜 파서를 통째로 공용화하지 않나</h2>
 *
 * <p>클라이언트마다 성공 코드·필드명·예외 문구가 다르고, 그건 각자가 아는 것이 맞다. 공유할 만한 것은
 * <b>모든 data.go.kr 응답이 똑같이 쓰는 이 envelope</b> 하나뿐이라 여기만 뗀다.
 *
 * <p><b>인증키는 이 envelope 에 들어 있지 않다</b> — 그래서 그대로 로그에 실어도 된다(로깅 규약).
 */
public final class DataGoKrError {

    private static final String ENVELOPE = "OpenAPI_ServiceResponse";
    private static final String HEADER = "cmmMsgHeader";
    private static final String UNKNOWN = "?";

    private DataGoKrError() {
    }

    /**
     * 응답 루트에서 게이트웨이 사유를 꺼낸다 — <b>붙일 것이 없으면 빈 문자열</b>.
     *
     * <p>빈 문자열을 주는 것이 핵심이다. 호출자가 {@code "...resultCode=%s%s"} 처럼 이어 붙이므로,
     * 평범한 실패(코드는 왔는데 성공이 아닌 경우)에는 아무것도 안 붙는다.
     *
     * @param root 파싱한 응답 전체. {@code response} 노드가 아니라 그 바깥이다
     */
    public static String of(JsonNode root) {
        JsonNode header = root.path(ENVELOPE).path(HEADER);
        if (header.isMissingNode()) {
            return "";
        }
        return " errMsg=%s reasonCode=%s"
                .formatted(header.path("errMsg").asText(UNKNOWN),
                        header.path("returnReasonCode").asText(UNKNOWN));
    }
}
