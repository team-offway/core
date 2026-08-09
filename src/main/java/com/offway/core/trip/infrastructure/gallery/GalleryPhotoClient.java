package com.offway.core.trip.infrastructure.gallery;

import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import java.util.List;

/**
 * 관광사진 갤러리 조회 port(#196). 구현은 {@link GalleryPhotoClientImpl}.
 *
 * <p>전량이 6,118건(실측)이라 페이지로 나눠 통째로 받아 DB 에 넣는다 — 요청 경로에서는 부르지 않는다.
 */
public interface GalleryPhotoClient {

    /**
     * 한 페이지를 받는다.
     *
     * @param pageNo 1부터
     * @param rows 페이지 크기
     * @return 그 페이지의 사진들. 키가 없거나 결과가 없으면 빈 목록
     */
    List<GalleryPhotoItem> findPage(int pageNo, int rows);
}
