package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.config.BatchBudgetProperties;
import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.domain.PoiIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
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

    /** 이 배치가 태운 외부 호출에 붙는 이름(#285). 알림에 그대로 실리므로 사람이 읽는 말로 둔다. */
    private static final Caller CALLER = Caller.of("장소운영시간배치");

    /**
     * 하루에 쓸 호출 수 — 관광정보 한도(1,000)의 30%.
     *
     * <p>나머지는 사용자 요청(코스 생성 1건당 3회)과 장소 상세가 쓴다. 이 배치가 한도를 다 먹으면
     * 정작 코스가 안 나온다 — 채우려던 값 때문에 채울 대상이 사라지는 셈이다.
     *
     * <p><b>로컬은 이보다 적게 쓴다.</b> 로컬과 운영이 같은 키를 쓰는데 배치 건너뛰기는 자기 DB 안에서만
     * 중복을 막아, 그대로 두면 두 곳이 각자 하루치를 태운다(#254). {@code offway.batch.regions-per-run} 이
     * 설정돼 있으면 그만큼으로 줄인다.
     */
    private static final int DAILY_BUDGET = 300;

    /** "지금" 판정은 KST — 한도 리셋 경계와 같은 기준이라야 한다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private static final Duration MIN_INTERVAL = Duration.ofDays(1);

    /**
     * 홈 카드용으로 지역·칩마다 받아 둘 건수(#305).
     *
     * <p>홈이 칩마다 그만큼만 보여주므로 더 받아도 화면에 안 나온다. 이 값이 곧 회차당 콜 수를 정한다 —
     * {@code 89곳 × 칩 4개 × 2건 = 712콜} 이고, 하루 예산({@value #DAILY_BUDGET})으로는 세 회차쯤 걸린다.
     *
     * <p><b>전량을 받지 않는 이유.</b> 지역당 등록 건수가 중앙값 57건이라 전부면 5,000콜이고 일일 한도로는
     * 닷새치다. 화면에 안 나올 것까지 받으면서 코스 생성 몫을 밀어낼 이유가 없다.
     */
    private static final int CARDS_PER_CATEGORY = 2;

    private final TourApiClient tourApiClient;
    private final PoiIntroRepository poiIntroRepository;
    private final BatchRunRepository batchRunRepository;
    private final BatchBudgetProperties batchBudget;

    /**
     * 하루 한 번 — 그날 이미 돌았으면 외부를 아예 안 부른다.
     *
     * <p>{@code fixedDelay} 는 프로세스가 사는 동안의 간격이라 재배포하면 주기가 처음부터 다시 센다.
     * 그것만 믿으면 배포가 잦은 날 예산을 여러 번 쓴다(#226·#231 에서 겪었다).
     */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = REFRESH_INTERVAL)
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
            if (batchRunRepository.hasRunSince(BATCH_NAME, now.minus(MIN_INTERVAL))) {
                log.info("장소 운영시간을 최근 {}에 이미 받아 건너뜁니다", MIN_INTERVAL);
                return;
            }
            // 결과가 아니라 실행을 기록한다 — 전부 실패한 회차에 아무것도 안 써지면 재부팅마다 다시 쏜다.
            batchRunRepository.markStarted(BATCH_NAME, now);
            refresh();
        });
    }

    /**
     * 아직 안 받은 콘텐츠를 예산만큼 받는다 — <b>건너뛰기 없이</b>.
     *
     * <p>순차로 부른다. 병렬로 밀어붙이면 429 를 맞고(실측: 200ms 안에 18건이면 제공기관이 던진다),
     * 그건 사용자 요청까지 함께 막는다. 배치라 사용자를 기다리게 하지 않으므로 느려도 된다.
     */
    public void refresh() {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        // 로컬은 한 회차에 몇 건만 채운다(#254) — 자세한 이유는 BatchBudgetProperties.
        int budget = batchBudget.limits(DAILY_BUDGET) ? batchBudget.regionsPerRun() : DAILY_BUDGET;
        // 코스 슬롯이 먼저다 — 사용자가 방금 만들어 지금 보고 있는 장소다. 홈 카드는 그 뒤 순서로도
        // 며칠 안에 찬다. 반대로 두면 새 코스의 운영시간이 홈 뒤에 밀린다.
        int used = fill(poiIntroRepository.findMissing(budget, now), now, "코스 장소");

        int left = budget - used;
        if (left <= 0) {
            log.info("홈 카드 부제 — 이번 회차 예산을 코스 장소가 다 썼습니다(예산 {}건)", budget);
            return;
        }
        fill(poiIntroRepository.findMissingForCards(left, CARDS_PER_CATEGORY, now), now, "홈 카드 부제");
    }

    /**
     * 일감을 예산만큼 받아 저장한다 — <b>건너뛰기 없이</b>.
     *
     * <p>순차로 부른다. 병렬로 밀어붙이면 429 를 맞고(실측: 200ms 안에 18건이면 제공기관이 던진다),
     * 그건 사용자 요청까지 함께 막는다. 배치라 사용자를 기다리게 하지 않으므로 느려도 된다.
     *
     * @param label 로그에 남길 일감 이름 — 두 일감이 같은 문장을 쓰므로 어느 쪽인지 갈려야 한다
     * @return 실제로 부른 외부 호출 수. 남은 예산 계산에 쓰인다
     */
    private int fill(List<PoiIntroRepository.ContentRef> missing, LocalDateTime now, String label) {
        if (missing.isEmpty()) {
            log.info("{} — 받을 것이 없습니다(저장 누계 {}건)", label, poiIntroRepository.count());
            return 0;
        }
        Map<PoiIntroRepository.ContentRef, PoiIntro> fetched = new HashMap<>();
        int failed = 0;
        int empty = 0;
        for (PoiIntroRepository.ContentRef ref : missing) {
            PoiIntro intro;
            try {
                // 받은 것을 좁히지 않는다 — 예전에는 운영시간 둘만 남기고 나머지를 버렸는데,
                // 그 나머지가 홈 카드 부제의 재료다(#305). 같은 콜로 이미 오는 값이다.
                intro = tourApiClient.findIntro(ref.contentId(), ref.contentTypeId())
                        .map(TourIntro::toPoiIntro)
                        .orElse(null);
            } catch (RuntimeException e) {
                failed++;
                continue; // 다음 회차에 다시 시도한다 — 저장하지 않으면 여전히 "안 받은 것" 이다
            }
            if (intro == null || intro.isEmpty()) {
                // 빈 행으로 남긴다 — 매 회차 다시 물으면 예산을 태우기 때문이다. 다만 캐시가 아니라
                // 재시도 대기다: next_retry_at 이 되면 다시 일감이 된다. 물을수록 그 간격이 벌어진다(#368).
                empty++;
                fetched.put(ref, PoiIntro.builder().build());
                continue;
            }
            fetched.put(ref, intro);
        }

        int saved = poiIntroRepository.upsertAll(fetched, now);
        if (failed > 0 || empty > 0) {
            // 빈 응답도 warn 이다. info 로 묻으면 "적재 성공" 처럼 보여, 화면이 왜 비는지 아무도 모른 채 굳는다.
            // 재시도 시점은 행마다 다르다(#368) — 계속 비는 장소일수록 다음 차례가 멀어진다.
            log.warn("{} 적재 {}건(대상 {}) — 실패 {}건·값없음 {}건(간격을 늘려 재시도), 저장 누계 {}건",
                    label, saved, missing.size(), failed, empty, poiIntroRepository.count());
        } else {
            log.info("{} 적재 {}건(대상 {}) — 저장 누계 {}건",
                    label, saved, missing.size(), poiIntroRepository.count());
        }
        // 부른 만큼이 예산 소비다. 빈 응답도 콜을 썼으므로 저장 건수가 아니라 대상 수를 돌려준다 —
        // saved 를 돌려주면 빈 응답이 많은 회차에 남은 예산을 실제보다 크게 본다.
        return missing.size();
    }
}
