package com.offway.core.curation.controller.dto;

import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.curation.domain.Surface;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * 백오피스가 보는 큐레이션 링크(#342) — <b>앱이 보는 것보다 넓다</b>.
 *
 * <p>{@link CuratedLinkResponse} 는 앱이 그릴 것만 담는다. 여기는 <b>고칠 수 있어야</b> 하므로 게시 여부·
 * 기간·면·정렬처럼 판정에 쓰이는 값까지 전부 내린다. 두 응답을 하나로 합치면 앱 응답에 미공개 여부가 딸려
 * 나가고, 그건 팀 밖에 알릴 이유가 없는 값이다.
 *
 * @param updatedBy 마지막으로 고친 어드민. <b>seed 로 들어온 행은 null</b> 이다 — 사람이 손댄 적이 없다
 */
@Builder
public record AdminCuratedLinkResponse(
        @Schema(example = "3") long id,
        @Schema(example = "2026 대한민국 숙박세일 페스타") String title,
        @Schema(example = "숙박 3만원 할인") String chipText,
        @Schema(nullable = true) String description,
        @Schema(example = "https://ktostay.visitkorea.or.kr") String linkUrl,
        @Schema(nullable = true) String thumbnailUrl,
        @Schema(example = "2026-06-11", nullable = true) LocalDate startsOn,
        @Schema(example = "2026-08-31", nullable = true) LocalDate endsOn,
        @Schema(example = "false") boolean alwaysOn,
        @Schema(example = "[\"HOME\",\"REGION\"]") Set<Surface> surfaces,
        @Schema(example = "10") int displayOrder,
        @Schema(example = "true") boolean published,
        @Schema(example = "박세빈", nullable = true) String updatedBy) {

    /**
     * 빌더로 조립하는 이유 — 문자열 필드가 여섯이고 boolean 이 둘이라, 위치 인수면 순서가 뒤바뀌어도
     * 컴파일이 통과한다. 특히 {@code alwaysOn}·{@code published} 가 뒤집히면 화면이 정반대를 보여준다.
     */
    public static AdminCuratedLinkResponse from(CuratedLink link) {
        return AdminCuratedLinkResponse.builder()
                .id(link.getId())
                .title(link.getTitle())
                .chipText(link.getChipText())
                .description(link.getDescription())
                .linkUrl(link.getLinkUrl())
                .thumbnailUrl(link.getThumbnailUrl())
                .startsOn(link.getStartsOn())
                .endsOn(link.getEndsOn())
                .alwaysOn(link.isAlwaysOn())
                .surfaces(link.surfacesOf())
                .displayOrder(link.getDisplayOrder())
                .published(link.isPublished())
                .updatedBy(link.getUpdatedBy())
                .build();
    }

    public static List<AdminCuratedLinkResponse> from(List<CuratedLink> links) {
        return links.stream().map(AdminCuratedLinkResponse::from).toList();
    }
}
