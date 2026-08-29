package com.offway.core.curation.controller.dto;

import com.offway.core.curation.domain.CuratedLink;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 큐레이션 링크 항목 — API 계약(#341).
 *
 * <p>홈·지역 상세·코스 상세·장소 상세 네 응답이 같은 모양으로 싣는다. 별도 엔드포인트를 만들지 않는 이유는
 * 화면 안에서 함께 렌더되기 때문이다 — 따로 부르게 하면 앱이 화면마다 왕복을 하나 더 한다.
 *
 * <p><b>없는 값은 키가 빠지는 게 아니라 {@code null} 로 실린다.</b> 이 레포는 필드를 빼지 않는다.
 *
 * @param id 항목 ID
 * @param title 카드 제목
 * @param chipText 칩 문구 — 목록에서 처음 읽는 한 줄
 * @param description 카드 부제. <b>없으면 null</b> — 앱이 그 줄을 그리지 않는다
 * @param linkUrl 누르면 웹뷰로 열 주소. 언제나 {@code https}
 * @param thumbnailUrl 썸네일. <b>없으면 null</b> — 앱이 기본 이미지를 쓴다
 */
public record CuratedLinkResponse(
        @Schema(example = "3") long id,
        @Schema(example = "2026 대한민국 숙박세일 페스타") String title,
        @Schema(example = "숙박 3만원 할인") String chipText,
        @Schema(example = "전국 숙박 할인권을 선착순으로 나눠 준다", nullable = true) String description,
        @Schema(example = "https://ktostay.visitkorea.or.kr") String linkUrl,
        @Schema(example = "https://ktostay.visitkorea.or.kr/thumb.jpg", nullable = true) String thumbnailUrl) {

    public static CuratedLinkResponse from(CuratedLink link) {
        return new CuratedLinkResponse(
                link.getId(),
                link.getTitle(),
                link.getChipText(),
                link.getDescription(),
                link.getLinkUrl(),
                link.getThumbnailUrl());
    }

    /** 네 응답이 같은 줄을 반복하지 않도록 목록 변환을 여기 둔다. */
    public static List<CuratedLinkResponse> of(List<CuratedLink> links) {
        return links.stream().map(CuratedLinkResponse::from).toList();
    }
}
