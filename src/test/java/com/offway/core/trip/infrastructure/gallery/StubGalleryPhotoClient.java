package com.offway.core.trip.infrastructure.gallery;

import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import com.offway.core.trip.infrastructure.gallery.dto.GallerySearch;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link GalleryPhotoClient} 외부 경계 stub — 통합 테스트에서 갤러리 호출을 격리한다. default 는 throw 라
 * 명시 세팅을 빠뜨리면 즉시 깨진다.
 */
public class StubGalleryPhotoClient implements GalleryPhotoClient {

    private BiFunction<Integer, Integer, List<GalleryPhotoItem>> behavior = (pageNo, rows) -> {
        throw new IllegalStateException("StubGalleryPhotoClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(BiFunction<Integer, Integer, List<GalleryPhotoItem>> behavior) {
        this.behavior = behavior;
    }

    /**
     * 키워드 검색의 기본은 <b>빈 목록</b>이다(#450).
     *
     * <p>다른 stub 과 달리 throw 로 두지 않는다 — 사진은 카드의 곁가지라 없는 것이 정상 경로이고,
     * 코스를 만드는 수많은 기존 테스트가 이 값을 안 정한다. 여기서 던지면 그것들이 전부 깨지는데,
     * 깨진 이유가 "사진 stub 을 안 정했다" 라 시나리오와 무관하다.
     */
    private Function<String, GallerySearch> searchBehavior = keyword -> GallerySearch.asked(List.of());

    /** 물어봤고 이것을 받았다 — 빈 목록이면 "그 지점의 사진이 없다" 다. */
    public void respondToSearch(Function<String, List<GalleryPhotoItem>> searchBehavior) {
        this.searchBehavior = keyword -> GallerySearch.asked(searchBehavior.apply(keyword));
    }

    /** 못 물어봤다 — 키가 없거나 조회가 실패한 상황(#535). */
    public void failSearch() {
        this.searchBehavior = keyword -> GallerySearch.notAsked();
    }

    @Override
    public List<GalleryPhotoItem> findPage(int pageNo, int rows) {
        return behavior.apply(pageNo, rows);
    }

    @Override
    public GallerySearch searchByKeyword(String keyword, int rows) {
        return searchBehavior.apply(keyword);
    }
}
