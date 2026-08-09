package com.offway.core.trip.infrastructure.gallery;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 이미지 URL 생존 확인 adapter(#196).
 *
 * <p><b>HEAD 를 쓰지 않는다.</b> 이 호스트는 HEAD 에 405 를 돌려주므로 살아 있는 URL 도 죽은 것으로
 * 판정된다(실측에서 83개 전부가 405 였다). GET 으로 확인하되 본문은 버린다.
 */
@Slf4j
@Component
class GalleryImageVerifierImpl implements GalleryImageVerifier {

    /** 한 장 확인의 상한. 이미지 서버라 정상이면 수백 ms 안에 응답한다. */
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    /**
     * 동시 확인 수.
     *
     * <p>1,790장을 순차로 돌면 지연이 그만큼 곱해진다(실측 기준 3분 이상). 반대로 무제한으로 열면 남의
     * 이미지 서버에 부담을 준다 — 주 1회 배치라 이 정도면 충분히 빠르다(수십 초).
     */
    private static final int CONCURRENCY = 16;

    /**
     * 확인 <b>전체</b>의 시간 상한.
     *
     * <p>호출 하나의 timeout 과 작업 전체의 deadline 은 별개다. 상대가 느려지면 장당 8초가 쌓여 배치가
     * 끝나지 않으므로 전체 상한을 따로 둔다. 넘으면 그때까지 확인된 것만 쓴다.
     */
    private static final Duration TOTAL_DEADLINE = Duration.ofMinutes(3);

    private final WebClient webClient;

    GalleryImageVerifierImpl(WebClient externalWebClient) {
        this.webClient = externalWebClient;
    }

    @Override
    public Set<String> aliveUrls(List<String> urls) {
        if (urls.isEmpty()) {
            return Set.of();
        }
        Set<String> alive = ConcurrentHashMap.newKeySet();
        Flux.fromIterable(urls)
                .flatMap(url -> isAlive(url).doOnNext(ok -> {
                    if (ok) {
                        alive.add(url);
                    }
                }), CONCURRENCY)
                .blockLast(TOTAL_DEADLINE);
        int dead = urls.size() - alive.size();
        if (dead > 0) {
            // 조용히 버리면 원본 품질이 나빠진 것을 아무도 모른다. 비율이 뛰면 여기서 드러난다.
            log.info("갤러리 이미지 생존 확인 — 살아있음 {}/{}건(죽은 URL {}건 제외)", alive.size(), urls.size(), dead);
        }
        return alive;
    }

    private Mono<Boolean> isAlive(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .map(response -> response.getStatusCode().is2xxSuccessful())
                // 404·timeout·연결 실패 모두 "확인 못 함" 이다. 살아 있다고 단정하지 않는다 — 그래야
                // 깨진 이미지가 카드로 나가지 않는다.
                .onErrorReturn(false);
    }
}
