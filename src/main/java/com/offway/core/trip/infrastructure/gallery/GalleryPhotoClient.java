package com.offway.core.trip.infrastructure.gallery;

import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import com.offway.core.trip.infrastructure.gallery.dto.GallerySearch;
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

    /**
     * 키워드로 사진을 찾는다(#450) — {@code gallerySearchList1}.
     *
     * <p>목록 전량 적재({@link #findPage})와 다른 오퍼레이션이다. 역·터미널처럼 <b>지역이 아닌 지점</b>의
     * 사진은 전량에서 골라내기 어렵다 — 갤러리 6,115장을 이름으로 훑느니 그 이름으로 직접 묻는 편이
     * 정확하고, 검색은 {@code galSearchKeyword} 까지 본다.
     *
     * @param keyword 찾을 말 — 역·터미널 이름을 그대로 넘긴다
     * @param rows 최대 건수
     * @return 물어봤는지와 받은 것을 함께. <b>예외를 던지지 않는다</b> — 사진이 없는 지점이 흔하고,
     *     사진 한 장 때문에 배치 전체가 멈추면 안 된다. 다만 <b>못 물어본 것과 없는 것은 갈라</b>
     *     돌려준다(#535) — 호출자가 "없음" 으로 적어도 되는지가 거기서 갈린다
     */
    GallerySearch searchByKeyword(String keyword, int rows);
}
