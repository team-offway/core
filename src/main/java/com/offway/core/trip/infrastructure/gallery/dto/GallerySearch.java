package com.offway.core.trip.infrastructure.gallery.dto;

import java.util.List;

/**
 * 갤러리 검색 한 번의 결과 — <b>물어봤는지</b>와 무엇을 받았는지를 함께 답한다(#535).
 *
 * <h2>왜 둘을 갈라야 하나</h2>
 *
 * <p>예전에는 셋을 모두 빈 목록으로 돌려줬다.
 *
 * <ul>
 *   <li>키가 없어 <b>묻지도 못한</b> 경우
 *   <li>물었는데 <b>실패한</b> 경우
 *   <li>물었는데 <b>사진이 없는</b> 경우
 * </ul>
 *
 * <p>호출자는 셋을 못 가르니 전부 "없음" 으로 적었다. 그래서 운영에서 교통 거점 155곳이 <b>한 번도 안
 * 물어본 채</b> "물어봤는데 없음" 으로 3일마다 다시 기록됐다 — 4주치 호출 기록에 이 배치의 갤러리
 * 호출이 <b>0건</b>인데도 그랬다.
 *
 * <p>그게 이 레포가 금지하는 조용한 실패다. 없다는 사실과 못 물었다는 사실은 <b>다음에 무엇을 할지</b>가
 * 다르다 — 앞은 그대로 두면 되고, 뒤는 다시 물어야 한다.
 *
 * @param asked 갤러리에 실제로 물어봤는가. {@code false} 면 {@code items} 는 아무 뜻이 없다
 * @param items 받은 사진들. 물어봤는데 없으면 빈 목록
 */
public record GallerySearch(boolean asked, List<GalleryPhotoItem> items) {

    private static final GallerySearch NOT_ASKED = new GallerySearch(false, List.of());

    public GallerySearch {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 물어봤다 — 결과가 비어 있으면 "그 지점의 사진이 없다" 가 참이다. */
    public static GallerySearch asked(List<GalleryPhotoItem> items) {
        return new GallerySearch(true, items);
    }

    /** 못 물어봤다 — 키가 없거나 조회가 실패했다. <b>없음으로 적으면 안 된다.</b> */
    public static GallerySearch notAsked() {
        return NOT_ASKED;
    }
}
