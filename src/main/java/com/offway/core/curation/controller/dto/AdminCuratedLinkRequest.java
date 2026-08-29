package com.offway.core.curation.controller.dto;

import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.curation.domain.Surface;
import com.offway.core.curation.service.dto.CuratedLinkCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/**
 * 큐레이션 링크 생성·수정 요청(#342) — 생성과 수정이 <b>같은 모양</b>이다.
 *
 * <p>수정이 부분 갱신이 아니라 전체 교체이기 때문이다. 기간 규칙(상시가 아니면 종료일 필수)처럼 여러 필드가
 * 함께 봐야 성립하는 불변식이 있어, 한 필드만 바꾸면 나머지와 어긋난 상태가 만들어진다. 화면도 폼 전체를
 * 들고 있으므로 전부 받는 편이 자연스럽다.
 *
 * <h2>여기서 검증하는 것과 안 하는 것</h2>
 *
 * <p>여기는 <b>모양</b>만 본다 — 비었는지, 길이가 컬럼을 넘는지. {@code https} 스킴·기간 규칙처럼 <b>값들
 * 사이의 관계</b>는 도메인이 본다. 그쪽이 seed SQL 로 들어오든 이 폼으로 들어오든 같은 규칙을 받는 자리다.
 *
 * <h2>선택 필드는 primitive 를 쓰지 않는다</h2>
 *
 * <p>이 스택은 Jackson 3 이고 {@code FAIL_ON_NULL_FOR_PRIMITIVES} 가 켜져 있다. {@code boolean}·{@code int}
 * 로 두면 JSON 에 그 필드가 <b>없을 때</b> 매핑이 깨져 요청 전체가 400 이 된다 — 필드 하나를 생략한 것이
 * 값 오류로 보고되는 셈이라, 어드민은 어디가 틀렸는지 알 수 없다. 래퍼로 받고 기본값은 여기서 정한다.
 *
 * @param title 카드 제목
 * @param chipText 칩 문구 — 목록에서 처음 읽는 한 줄
 * @param description 카드 부제. 비우면 앱이 그 줄을 안 그린다
 * @param linkUrl 웹뷰로 열 주소. {@code https} 만 받는다(도메인 검증 → 400 {@code CURATION-001})
 * @param thumbnailUrl 썸네일. 비우면 앱이 기본 이미지를 쓴다
 * @param startsOn 노출 시작일. 비우면 "이미 시작했다" 로 읽는다
 * @param endsOn 노출 종료일. {@code alwaysOn} 이 거짓이면 <b>필수</b>({@code CURATION-002})
 * @param alwaysOn 상시 노출인가. 날짜를 비운 것과 구분하려고 따로 받는다. 안 보내면 거짓
 * @param surfaces 내릴 화면. 하나 이상({@code CURATION-005})
 * @param displayOrder 같은 면 안의 정렬. 작을수록 앞. 안 보내면 0
 * @param published 앱에 내릴지. 안 보내면 <b>거짓</b> — 만들다 만 것이 바로 보이면 안 된다
 */
public record AdminCuratedLinkRequest(
        @Schema(example = "2026 대한민국 숙박세일 페스타", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank @Size(max = 100) String title,
        @Schema(example = "숙박 3만원 할인", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank @Size(max = CuratedLink.MAX_CHIP_TEXT_LENGTH) String chipText,
        @Schema(example = "전국 숙박 할인권을 선착순으로 나눠 준다", nullable = true)
                @Size(max = 500) String description,
        @Schema(example = "https://ktostay.visitkorea.or.kr", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank @Size(max = 1000) String linkUrl,
        @Schema(example = "https://ktostay.visitkorea.or.kr/thumb.jpg", nullable = true)
                @Size(max = 1000) String thumbnailUrl,
        @Schema(example = "2026-06-11", nullable = true) LocalDate startsOn,
        @Schema(example = "2026-08-31", nullable = true) LocalDate endsOn,
        @Schema(example = "false", nullable = true) Boolean alwaysOn,
        @Schema(example = "[\"HOME\",\"REGION\"]", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotEmpty Set<Surface> surfaces,
        @Schema(example = "10", nullable = true) Integer displayOrder,
        @Schema(example = "true", nullable = true) Boolean published) {

    /** 안 보내면 기간이 있는 항목으로 본다 — 상시는 명시적 선택이어야 한다(#217). */
    private static final boolean DEFAULT_ALWAYS_ON = false;

    /** 안 보내면 맨 앞. 정렬을 안 정한 항목이 목록 끝으로 밀려 안 보이는 것보다 낫다. */
    private static final int DEFAULT_DISPLAY_ORDER = 0;

    /** <b>안 보내면 안 내린다.</b> 만들다 만 항목이 곧바로 사용자에게 보이면 안 된다. */
    private static final boolean DEFAULT_PUBLISHED = false;

    public CuratedLinkCommand toCommand() {
        return new CuratedLinkCommand(
                title,
                chipText,
                description,
                linkUrl,
                thumbnailUrl,
                startsOn,
                endsOn,
                alwaysOn != null ? alwaysOn : DEFAULT_ALWAYS_ON,
                surfaces,
                displayOrder != null ? displayOrder : DEFAULT_DISPLAY_ORDER,
                published != null ? published : DEFAULT_PUBLISHED);
    }
}
