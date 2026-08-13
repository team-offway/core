package com.offway.core.trip.infrastructure.gallery;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * {@link GalleryImageVerifier} 외부 경계 stub — 통합 테스트에서 이미지 생존 확인(실 HTTP)을 격리한다.
 * default 는 throw 라 명시 세팅을 빠뜨리면 즉시 깨진다.
 */
public class StubGalleryImageVerifier implements GalleryImageVerifier {

    private Function<List<String>, Set<String>> behavior = urls -> {
        throw new IllegalStateException("StubGalleryImageVerifier 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(Function<List<String>, Set<String>> behavior) {
        this.behavior = behavior;
    }

    /** 전부 살아 있다고 본다 — 생존 확인이 관심사가 아닌 테스트용. */
    public void allAlive() {
        this.behavior = Set::copyOf;
    }

    @Override
    public Set<String> aliveUrls(List<String> urls) {
        return behavior.apply(urls);
    }
}
