package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.repository.PoiIntroRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 코스에 쓰인 장소의 운영시간·휴무일을 조금씩 받아 둔다(#157).
 *
 * <p><b>왜 배치인가.</b> 슬롯마다 요청 시점에 부르면 코스 하나에 20건이 넘는다. 관광정보 한도가 하루
 * 1,000이라 코스 40개면 마른다. 운영시간은 콘텐츠에 붙는 값이고 거의 안 변하므로, 콘텐츠당 한 번만 받으면
 * 같은 장소가 여러 코스에 나와도 다시 안 부른다.
 *
 * <p><b>일감은 슬롯 테이블이다.</b> 별도 큐를 두지 않는다 — 우리가 알아야 하는 콘텐츠는 정확히 "코스에
 * 실제로 쓰인 것" 이고 그건 이미 슬롯에 남아 있다.
 *
 * <p><b>처음엔 비어 있다.</b> 화면은 있으면 보여주고 없으면 그 줄을 지운다. 하루 예산만큼 메우므로 며칠에
 * 걸쳐 찬다 — 없는 것을 지어내는 것보다 늦게 채워지는 편이 낫다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PoiIntroRefreshService {

    /** 부팅 후 첫 실행까지 지연 — 기동·헬스체크를 방해하지 않게. */
    private static final String INITIAL_DELAY = "PT90S";

    /** 실행 간격. 하루 한 번이면 예산을 쓰는 속도가 예측 가능하다. */
    private static final String REFRESH_INTERVAL = "P1D";

    static final String BATCH_NAME = "poi-intro-refresh";

    /**
     * 하루에 쓸 호출 수 — 관광정보 한도(1,000)의 30%.
     *
     * <p>나머지는 사용자 요청(코스 생성 1건당 3회)과 장소 상세가 쓴다. 이 배치가 한도를 다 먹으면
     * 정작 코스가 안 나온다 — 채우려던 값 때문에 채울 대상이 사라지는 셈이다.
     */
    private static final int DAILY_BUDGET = 300;

    /** "지금" 판정은 KST — 한도 리셋 경계와 같은 기준이라야 한다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private static final Duration MIN_INTERVAL = Duration.ofDays(1);

    private final TourApiClient tourApiClient;
    private final PoiIntroRepository poiIntroRepository;
    private final BatchRunRepository batchRunRepository;

    /**
     * 하루 한 번 — 그날 이미 돌았으면 외부를 아예 안 부른다.
     *
     * <p>{@code fixedDelay} 는 프로세스가 사는 동안의 간격이라 재배포하면 주기가 처음부터 다시 센다.
     * 그것만 믿으면 배포가 잦은 날 예산을 여러 번 쓴다(#226·#231 에서 겪었다).
     */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = REFRESH_INTERVAL)
    public void refreshIfStale() {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        if (batchRunRepository.hasRunSince(BATCH_NAME, now.minus(MIN_INTERVAL))) {
            log.info("장소 운영시간을 최근 {}에 이미 받아 건너뜁니다", MIN_INTERVAL);
            return;
        }
        // 결과가 아니라 실행을 기록한다 — 전부 실패한 회차에 아무것도 안 써지면 재부팅마다 다시 쏜다.
        batchRunRepository.markStarted(BATCH_NAME, now);
        refresh();
    }

    /**
     * 아직 안 받은 콘텐츠를 예산만큼 받는다 — <b>건너뛰기 없이</b>.
     *
     * <p>순차로 부른다. 병렬로 밀어붙이면 429 를 맞고(실측: 200ms 안에 18건이면 제공기관이 던진다),
     * 그건 사용자 요청까지 함께 막는다. 배치라 사용자를 기다리게 하지 않으므로 느려도 된다.
     */
    public void refresh() {
        List<PoiIntroRepository.ContentRef> missing = poiIntroRepository.findMissing(DAILY_BUDGET);
        if (missing.isEmpty()) {
            log.info("장소 운영시간 — 받을 것이 없습니다(저장={}건)", poiIntroRepository.count());
            return;
        }

        Map<PoiIntroRepository.ContentRef, OpeningHours> fetched = new HashMap<>();
        int failed = 0;
        int empty = 0;
        for (PoiIntroRepository.ContentRef ref : missing) {
            OpeningHours hours;
            try {
                hours = tourApiClient.findIntro(ref.contentId(), ref.contentTypeId())
                        .map(intro -> new OpeningHours(intro.useTime(), intro.restDate()))
                        .orElse(null);
            } catch (RuntimeException e) {
                failed++;
                continue; // 다음 회차에 다시 시도한다 — 저장하지 않으면 여전히 "안 받은 것" 이다
            }
            if (hours == null || hours.isEmpty()) {
                // 값이 없다는 것도 사실이다. 안 넣으면 매 회차 같은 콘텐츠를 다시 물어 예산을 태운다.
                empty++;
                fetched.put(ref, new OpeningHours(null, null));
                continue;
            }
            fetched.put(ref, hours);
        }

        int saved = poiIntroRepository.upsertAll(fetched, LocalDateTime.now(SERVICE_ZONE));
        if (failed > 0) {
            log.warn("장소 운영시간 적재 {}건(대상 {}) — 실패 {}건·값없음 {}건, 저장 누계 {}건",
                    saved, missing.size(), failed, empty, poiIntroRepository.count());
            return;
        }
        log.info("장소 운영시간 적재 {}건(대상 {}) — 값없음 {}건, 저장 누계 {}건",
                saved, missing.size(), empty, poiIntroRepository.count());
    }
}
