package com.offway.core.liveactivity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * push-to-start 해제 요청 — <b>어느 기기를 끊을지</b>(#583).
 *
 * <p>누구인지는 access 토큰이 답하고, 이 본문은 <b>그 사람의 어느 기기인지</b>만 답한다.
 * {@code LogoutRequest}(#389)와 같은 모양이다 — 로그아웃이 기기별로 갈리는데 카드만 전부 끊으면
 * 폰에서 로그아웃한 사용자의 태블릿 잠금화면이 같이 비어, "아무것도 안 했는데 사라졌다" 가 된다.
 *
 * <p><b>토큰을 경로가 아니라 본문으로 받는다.</b> 이 값을 아는 쪽은 그 기기 잠금화면에 카드를 만들 수
 * 있어 비밀값에 준하는데, URL 에 실으면 프록시 접근 로그에 그대로 남는다(우리 Caddy 가 access.log 를
 * 남긴다). 본문은 안 남는다.
 *
 * <p>본문 전체가 선택이다. {@code @NotBlank} 를 붙이지 않는 이유가 그것이다 — 붙이면 "모든 기기에서
 * 해제" 를 부를 길이 없어진다.
 *
 * @param token 이 기기의 push-to-start 토큰. <b>없으면 이 사용자의 모든 기기</b>를 해제한다
 */
public record PushToStartUnregisterRequest(
        @Schema(description = "이 기기의 push-to-start 토큰. 비우면 모든 기기에서 해제된다", nullable = true)
                String token) {

    /** 본문이 통째로 없을 때와 필드만 없을 때를 호출부가 같게 다루도록. */
    public static String tokenOrNull(PushToStartUnregisterRequest request) {
        return request == null ? null : request.token();
    }
}
