package com.offway.core.trip.infrastructure.gallery.dto;

import com.offway.core.trip.domain.GalleryPhoto;
import java.time.LocalDateTime;

/**
 * 관광사진 갤러리 한 건(PhotoGalleryService1 응답).
 *
 * @param contentId 갤러리 식별자
 * @param title 사진 제목 — 장소명인 경우가 많지만 "봄의 약속" 처럼 작품명인 것도 있다
 * @param imageUrl 이미지 URL
 * @param photographyMonth 촬영월(yyyyMM). 없으면 null
 * @param photographyLocation 촬영 위치 <b>원문</b>(자유 텍스트). 없으면 null
 * @param photographer 촬영자. 없으면 null
 * @param searchKeyword 키워드 묶음 — 제목에 없는 장소명이 여기 있는 경우가 흔하다
 */
public record GalleryPhotoItem(
        String contentId,
        String title,
        String imageUrl,
        String photographyMonth,
        String photographyLocation,
        String photographer,
        String searchKeyword) {

    /**
     * 엔티티로 만들 수 있는가 — {@link GalleryPhoto} 생성자가 요구하는 값이 다 있는지 미리 본다.
     *
     * <p>어댑터가 이걸로 걸러야 한다. 통과시키면 적재 시점에 예외가 터져 한 건이 6,118건 전체를 멈춘다
     * (#195 에서 겪은 것과 같은 구조).
     */
    public boolean isComplete() {
        return hasText(contentId) && hasText(title) && hasText(imageUrl) && fitsColumns();
    }

    /**
     * 컬럼 길이에 들어가는가.
     *
     * <p>실측(2026-08-09)으로는 여유가 크다 — URL 최대 73자(컬럼 500), 키워드 167자(컬럼 2,000). 그래도
     * 보는 이유는 <b>비대칭</b> 때문이다. 적재는 한 트랜잭션이라 한 건이 컬럼을 넘으면 그 주 적재가 통째로
     * 실패하는데, 여기서 거르는 비용은 이 한 줄이다.
     */
    private boolean fitsColumns() {
        return within(contentId, 32)
                && within(title, 300)
                && within(imageUrl, 500)
                && within(photographyMonth, 6)
                && within(photographyLocation, 300)
                && within(photographer, 100)
                && within(searchKeyword, 2_000);
    }

    private static boolean within(String value, int max) {
        return value == null || value.length() <= max;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 영속 엔티티로. 지역은 아직 붙이지 않는다 — 정규화가 뒤에서 매긴다. */
    public GalleryPhoto toEntity(LocalDateTime updatedAt) {
        return GalleryPhoto.builder()
                .galContentId(contentId)
                .title(title)
                .imageUrl(imageUrl)
                .photographyMonth(photographyMonth)
                .photographyLocation(photographyLocation)
                .photographer(photographer)
                .searchKeyword(searchKeyword)
                .updatedAt(updatedAt)
                .build();
    }
}
