package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.config.BatchBudgetProperties;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.logging.DegradeTally;
import com.offway.core.common.logging.RootCause;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.HubAttraction;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.HubAttractionClient;
import com.offway.core.trip.infrastructure.datalab.dto.HubAttractionItem;
import com.offway.core.trip.repository.HubAttractionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Service;

/**
 * 중심 관광지를 받아 DB 에 적재한다(#185).
 *
 * <p><b>요청 경로에서 부르지 않는다.</b> 89개 지역 × 1회를 요청마다 하면 외부 한도가 금방 마른다.
 *
 * <p><b>주기를 원본의 발행 주기에 맞춘다</b>(#337). 원본은 월 단위 발행이라 갱신은 매월 1일 cron 이 소유하고,
 * 부팅 확인이 그 사이의 공백만 메운다. 예전에는 {@code fixedDelay = P1D} 로 하루 한 번 돌면서 이미 받은
 * 달을 다시 물었다 — 운영에서 <b>10일 연속 정확히 105콜</b>이 나갔고, 29일은 같은 답을 같은 값으로 덮었다.
 *
 * <p>그 위에 <b>기준월 마커</b>가 얹힌다: 목표월(또는 그 이후) 자료를 이미 가진 지역은 외부를 아예 안 부른다.
 * 이 판정은 <b>지역별</b>이다 — 예전에 "89곳이 <i>전부</i> 최신 달을 가졌는가" 로 물었다가, 지역마다 발행
 * 시점이 달라 그 조건이 사실상 참이 되지 않아 부팅마다 89회를 쐈다(#193 과 같은 원칙, 판정 단위만 고쳤다).
 *
 * <p><b>순차로 부른다.</b> 병렬로 던지면 초당 호출이 폭증해 429 를 맞는다 — 부팅 워밍에서 실제로 그랬다(#191).
 * 월 1회 배경 작업이라 89건을 순서대로 도는 시간은 아무도 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HubAttractionRefreshService {

    /**
     * 매달 1일 새벽 4시에 확인한다 — <b>원본이 월 단위 발행이라 주기를 거기에 맞춘다</b>(#337).
     *
     * <p>예전에는 {@code fixedDelay = P1D} 로 하루 한 번 돌았다. 그런데 월 단위로 바뀌는 값을 매일
     * 물으면 29일은 같은 답을 받아 같은 값으로 덮는다 — 운영에서 10일 연속 정확히 105콜이 나갔다.
     *
     * <p>사용자가 거의 없는 시각이라 105콜이 그날 사용자 몫을 밀어내지 않는다. {@code RegionPoiRefreshService}
     * 가 같은 이유로 같은 시각을 쓴다.
     */
    private static final String MONTHLY_AT_DAWN = "0 0 4 1 * *";

    /**
     * 부팅 후 첫 확인까지 지연 — 기동·헬스체크를 방해하지 않게.
     *
     * <p>cron 만 두면 그달 1일에 실패했거나 새로 배포된 지역이 <b>다음 달까지</b> 빈 채로 남는다. 부팅
     * 확인이 그 공백을 메운다 — 아래 기준월 마커가 이미 받은 지역을 걸러 내므로 대부분의 재배포는 0콜이다.
     */
    private static final String BOOT_CHECK_DELAY = "PT30S";

    /**
     * 부팅 확인의 반복 간격. 실질적으로 재발화하지 않는 값이다 — 주기 갱신은 {@link #MONTHLY_AT_DAWN} 이
     * 소유하고, 이 트리거는 "부팅했는데 아직 못 받은 곳이 있으면 채운다" 만 맡는다.
     */
    private static final String BOOT_CHECK_INTERVAL = "P30D";

    /** 지자체당 보관 순위 수. 원본은 100위까지 주지만 카드·코스에 쓰는 것은 상위 소수다. */
    private static final int ROWS_PER_REGION = 30;

    /**
     * 발행 지연을 감안해 물러설 개월 수 — 시작월 <b>포함</b> {@code MAX_MONTHS_BACK + 1}개 달을 확인한다.
     *
     * <p>이번 달 것은 아직 없을 수 있다. 지난달부터 시작해 빈 결과면 이전 달로 물러선다 — 고정 지연을 가정하면
     * 발행 공백에 걸려 <b>조용히</b> 빈 결과가 되고, 중심 관광지가 사라진다.
     */
    private static final int MAX_MONTHS_BACK = 3;

    /**
     * 배치 식별자 — {@code batch_run} 의 키다. 바꾸면 "한 번도 안 돈 것" 이 되어 그날 한 번 더 돈다.
     *
     * <p>같은 패키지의 통합 테스트가 이 값으로 실행 기록을 세팅한다 — 문자열을 테스트에 다시 적으면
     * 한쪽만 바뀌었을 때 테스트가 조용히 무의미해진다.
     */
    static final String BATCH_NAME = "hub-attraction-refresh";

    /** 이 배치가 태운 외부 호출에 붙는 이름(#285). 알림에 그대로 실리므로 사람이 읽는 말로 둔다. */
    private static final Caller CALLER = Caller.of("중심관광지배치");

    /** 발행일·"오늘" 판정 모두 KST. 서버 타임존을 따라가면 자정 근처에서 하루가 어긋난다. */
    private static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    private final HubAttractionClient hubAttractionClient;
    private final BatchRunRepository batchRunRepository;
    private final BatchBudgetProperties batchBudget;
    private final HubAttractionRepository hubAttractionRepository;
    private final RegionRepository regionRepository;

    /**
     * 하루 한 번 — <b>오늘 이미 돌았으면</b> 외부를 아예 안 부른다.
     *
     * <p>예전에는 "89곳이 전부 최신 달을 가졌는가" 로 판정했다. 그런데 이 API 는 지역마다 발행 시점이
     * 달라(전남 16곳이 한 달 늦는 식) 그 조건이 사실상 참이 되지 않았다 — 하나라도 빠지면 false 다.
     * 그래서 부팅할 때마다 89회를 다시 쐈고, 배포가 잦은 날은 그만큼 곱해졌다.
     *
     * <pre>
     *   15:24  갱신 완료 지역=50/89 기준월별={2026-06=3, 2026-07=47}
     *   16:07  0/89 — 전부 TooManyRequests
     *   16:13  0/89
     * </pre>
     *
     * <p>자기강화 고리였다: 건너뛰기 실패 → 89콜 → 한도 소진 → 더 많은 실패 → 최신 달을 못 채움 → 또 실패.
     *
     * <p><b>결과가 아니라 실행을 기록한다.</b> 적재 결과로 판정하면 전부 실패한 날에는 아무것도 안 써져
     * 다음 부팅이 또 89회를 쏜다 — 위 16:07·16:13 이 그것이다. 그래서 성공·실패를 가리지 않고 시작 시각을
     * 남긴다({@code finally}).
     *
     * <p>대가는 있다. 그날 실패한 지역은 그날 안에 다시 시도하지 않는다. 이전 값이 남아 화면은 유지되고
     * 처음부터 빈 지역만 하루를 기다리는데, 한도를 태워 <b>모두</b>가 실패하는 것보다 낫다고 봤다.
     * {@code HolidayRefreshService} 가 같은 판단으로 이미 돌고 있다(#193).
     */
    @Schedules({
        @Scheduled(cron = MONTHLY_AT_DAWN, zone = SERVICE_ZONE_ID),
        @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    })
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            LocalDate today = LocalDate.now(SERVICE_ZONE);
            // 확인과 기록을 한 문장으로 묶는다 — 트리거가 둘이라 "확인 → 105콜 → 기록" 사이의 창에 다른
            // 트리거가 들어오면 같은 날 두 번 쏜다. #315 가 같은 자리에서 같은 실수를 했다.
            if (!batchRunRepository.tryStartOn(BATCH_NAME, today, LocalDateTime.now(SERVICE_ZONE))) {
                log.info("중심 관광지를 오늘 이미 돌렸거나 다른 트리거가 선점해 갱신을 건너뜁니다 date={}", today);
                return;
            }
            // 선점이 곧 기록이다 — 아래가 실패해도 시각이 남아 같은 날 재부팅이 105콜을 다시 쏘지 않는다.
            refresh(newestPossibleMonth());
        });
    }

    /**
     * 전 지역을 다시 받아 적재한다.
     *
     * <p>지역마다 격리한다 — 한 곳이 실패해도 나머지는 채운다. 실패한 지역은 <b>이전 값을 그대로 둔다</b>.
     * 빈 목록으로 덮으면 그 지역 카드에서 대표 사진과 볼거리가 통째로 사라진다.
     */
    public void refresh(YearMonth target) {
        List<Region> all = regionRepository.findAll();
        if (all.isEmpty()) {
            return;
        }
        // 로컬은 한 회차에 몇 곳만 채운다(#254). 로컬과 운영이 같은 키를 쓰는데 hasRunSince 는 자기 DB
        // 안에서만 중복을 막아, 그대로 두면 두 곳이 각자 하루치를 태운다.
        List<Region> budgeted = batchBudget.limit(all);
        if (batchBudget.limits(all.size())) {
            log.info("중심 관광지 — 이번 회차는 {}/{}곳만 갱신합니다(로컬 예산)", budgeted.size(), all.size());
        }

        // 기준월 마커 — 목표월(또는 그 이후) 자료를 이미 가진 지역은 외부를 아예 안 부른다(#337).
        // 주석이 오래 전부터 "이미 최신이면 외부를 안 부른다" 고 적어 왔는데 코드에 그 스킵이 없었다.
        //
        // 판정을 **지역별**로 한다. 예전에 "89곳이 전부 최신 달을 가졌는가" 로 물었다가, 지역마다 발행
        // 시점이 달라 그 조건이 사실상 참이 되지 않아 부팅마다 89회를 쐈다 — 그 설계로 돌아가지 않는다.
        Set<Long> fresh = hubAttractionRepository.regionIdsFreshFrom(target);
        List<Region> regions = budgeted.stream()
                .filter(region -> !fresh.contains(region.getId()))
                .toList();
        if (regions.isEmpty()) {
            log.info("중심 관광지 — {}월치를 {}곳 모두 이미 받아 외부를 부르지 않습니다", target, budgeted.size());
            return;
        }
        if (regions.size() < budgeted.size()) {
            log.info("중심 관광지 — {}월치를 이미 가진 {}곳을 건너뜁니다(대상 {}곳)",
                    target, budgeted.size() - regions.size(), regions.size());
        }

        int filled = 0;
        int empty = 0;
        // 사유별로 센다 — 89곳이 같은 이유로 실패하면 WARN 은 사유마다 한 줄이면 되고, 대응은 건수가
        // 아니라 사유가 정한다(429 면 호출량, timeout 이면 외부 지연 — #224).
        DegradeTally failures = new DegradeTally();
        Map<YearMonth, Integer> byMonth = new TreeMap<>();
        for (Region region : regions) {
            try {
                Published published = fetchWithMonthFallback(region, target);
                if (published == null) {
                    // 성공 코드에 빈 결과가 오는 API 다 — 덮지 않고 이전 값을 남긴다. 집계만으로는 어느 지역이
                    // 왜 안 채워졌는지 모르므로 지역을 남긴다.
                    empty++;
                    log.warn("중심 관광지 빈 응답 — 이전 값을 유지합니다 regionId={} (최근 {}개월 모두 없음)",
                            region.getId(), MAX_MONTHS_BACK + 1);
                    continue;
                }
                hubAttractionRepository.replaceRegion(region.getId(), published.items().stream()
                        .map(item -> item.toEntity(region.getId(), published.month()))
                        .toList());
                byMonth.merge(published.month(), 1, Integer::sum);
                filled++;
            } catch (TourApiException e) {
                // 껍데기(TourApiException)만 찍으면 429 인지 timeout 인지 구분이 안 된다 — RootCause 가
                // 체인 끝의 실제 사유를, HTTP 면 응답 본문까지 마스킹해 남긴다.
                if (failures.add(RootCause.label(e))) {
                    log.warn("중심 관광지 갱신 실패 — 이전 값을 유지합니다 regionId={} cause={}",
                            region.getId(), RootCause.of(e));
                } else {
                    log.debug("중심 관광지 갱신 실패 — 이전 값을 유지합니다 regionId={} cause={}",
                            region.getId(), RootCause.of(e));
                }
            }
        }
        // 기준월 분포를 남긴다 — 지역마다 발행월이 다르므로 단일 baseYm 으로는 사실을 못 적는다.
        // 어떤 지역군이 이전 달로 물러섰는지가 여기서 드러난다.
        if (empty + failures.total() > 0) {
            log.warn("중심 관광지 갱신 완료 지역={}/{} 기준월별={} — 빈 응답 {}건·실패 {}건{}은 이전 값 유지",
                    filled, regions.size(), byMonth, empty, failures.total(), failures.summaryFragment());
            return;
        }
        log.info("중심 관광지 갱신 완료 지역={}/{} 기준월별={}", filled, regions.size(), byMonth);
    }

    /**
     * 그 지역이 <b>실제로 발행된</b> 달의 결과를 가져온다 — 지난달부터 시작해 비면 이전 달로 물러선다.
     *
     * <p><b>발행은 지역마다 다르다.</b> 실측(2026-08-09)에서 전남 16곳은 {@code 202607} 이 미발행이고
     * {@code 202606} 에만 있었는데, 나머지 지역은 {@code 202607} 이 있었다. 예전처럼 표본 몇 곳으로 달 하나를
     * 정해 전 지역에 쓰면 <b>전남이 통째로 빈 응답</b>이 되고, 빈 응답은 이전 값을 유지하므로 첫 적재에서는
     * 그 16곳이 영영 채워지지 않는다.
     *
     * <p>호출 비용은 지역당 1회가 기본이고, 물러선 지역만 그만큼 늘어난다(실측 기준 16곳이 2회).
     *
     * @return 발행된 달과 그 결과. {@value #MAX_MONTHS_BACK}개월을 물러서도 비면 null
     */
    private Published fetchWithMonthFallback(Region region, YearMonth start) {
        YearMonth month = start;
        // 경계를 포함한다 — 상수가 "물러설 개월 수" 이므로 시작월에서 그만큼 물러선 달까지 봐야 이름과 맞는다.
        for (int back = 0; back <= MAX_MONTHS_BACK; back++, month = month.minusMonths(1)) {
            List<HubAttractionItem> items =
                    hubAttractionClient.findByRegion(region.getLegalCode(), month, ROWS_PER_REGION);
            if (!items.isEmpty()) {
                return new Published(month, items);
            }
        }
        return null;
    }

    /** 한 지역이 실제로 받은 결과와 그 기준월 — 지역마다 다를 수 있어 짝으로 다닌다. */
    private record Published(YearMonth month, List<HubAttractionItem> items) {
    }

    /**
     * 존재할 수 있는 가장 새 달. 이번 달은 아직 집계 중이라 지난달이 최선이다.
     *
     * <p>KST 로 센다 — 이 값이 기준월 마커의 비교 대상이라, 서버 로케일을 타면 월말·월초 경계에서
     * 목표월이 한 달 어긋나 전 지역이 한 번 더 갱신 대상이 된다.
     */
    private static YearMonth newestPossibleMonth() {
        return YearMonth.from(LocalDate.now(SERVICE_ZONE)).minusMonths(1);
    }


    /** 지역의 중심 관광지 — 순위 오름차순. 아직 적재 전이면 빈 목록이다. */
    public List<HubAttraction> forRegion(Long regionId) {
        return hubAttractionRepository.findByRegionId(regionId);
    }
}
