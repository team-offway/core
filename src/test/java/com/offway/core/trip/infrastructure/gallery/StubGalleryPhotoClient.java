package com.offway.core.trip.infrastructure.gallery;

import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import java.util.List;
import java.util.function.BiFunction;

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

    @Override
    public List<GalleryPhotoItem> findPage(int pageNo, int rows) {
        return behavior.apply(pageNo, rows);
    }
}
