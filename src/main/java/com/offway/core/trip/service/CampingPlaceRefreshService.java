package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiBatchPolicy;
import com.offway.core.common.logging.RootCause;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.trip.domain.CampingPlace;
import com.offway.core.trip.infrastructure.camping.GoCampingClient;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsite;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsiteResult;
import com.offway.core.trip.repository.CampingPlaceRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 고캠핑 야영장을 받아 숙박 후보를 채운다(#510).
 *
 * <h2>왜 배치인가</h2>
 *
 * <p>축제(#433)와 같은 기준이다 — <b>원본이 배포보다 자주 바뀌나</b>. 야영장은 신규 등록·폐업·휴장이
 * 계속 생기고 원본이 수시로 갱신된다. 인허가 장소(121,393건)처럼 파일로 굳히기에는 변동이 잦고,
 * 요청 경로에서 부르기에는 7MB 를 받는 조회다.
 *
 * <h2>전국을 한 번에 받아 지역으로 나눈다</h2>
 *
 * <p>지역별로 부르면 회차마다 89번이다. 전국 3,115건이 <b>한 요청에</b> 오므로 회차당 1콜이면 된다.
 *
 * <h2>온전히 받았을 때만 정리한다</h2>
 *
 * <p>폐업·휴장으로 바뀐 야영장은 목록에서 빠지므로 upsert 만으로는 옛 행이 남는다. 다만 조회가
 * 실패하면 <b>"이번에 안 온 것 = 사라진 것" 이 성립하지 않는다</b> — 그때 지우면 멀쩡한 야영장을
 * 우리가 없앤다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampingPlaceRefreshService {

    private static final String SERVICE_ZONE_ID = "Asia/Seoul";
    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 매월 8일 새벽 4시 40분.
     *
     * <p>다른 배치와 시각을 벌렸다 — 지역 장소 풀이 매월 1일 04:00, 축제 기간이 화요일 04:20,
     * 축제 풀이 매월 6일 04:50 이다. 겹치면 새벽에 외부 호출이 한꺼번에 몰린다.
     */
    private static final String MONTHLY_AT_DAWN = "0 40 4 8 * *";

    /**
     * 부팅 뒤 확인 — 배포가 잦아 cron 을 놓칠 수 있다.
     *
     * <p>{@code fixedDelay} 는 재배포하면 주기가 처음부터 다시 센다(#226·#231). 아래 마커가 그것을 막는다.
     */
    private static final String BOOT_CHECK_DELAY = "PT360S";

    private static final String BOOT_CHECK_INTERVAL = "P7D";

    /** 이 주기 안에 이미 돌았으면 건너뛴다 — 재배포가 한도를 다시 태우지 않게. */
    private static final Duration RUN_INTERVAL = Duration.ofDays(25);

    private static final String BATCH_NAME = "camping-place-refresh";

    private static final Caller CALLER = Caller.of("야영장풀배치");

    /**
     * 조회 전체의 시간 상한.
     *
     * <p>실측 3.1초라 한참 여유가 있다. 호출 하나의 상한(60초)보다 길게 둬, 느려졌을 때 어느 쪽이
     * 먼저 끊는지가 헷갈리지 않게 한다.
     */
    private static final Duration TOTAL_DEADLINE = Duration.ofMinutes(2);

    private final GoCampingClient goCampingClient;
    private final CampingPlaceRepository campingPlaceRepository;
    private final RegionQuery regionQuery;
    private final BatchRunRepository batchRunRepository;

    /** 배치를 멈추거나 한도 상한을 거는 스위치(#403). */
    private final ExternalApiBatchPolicy batchPolicy;

    @Scheduled(cron = MONTHLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            if (!batchPolicy.batchMayCall(BATCH_NAME, ExternalApi.GO_CAMPING)) {
                // 조용히 넘기지 않는다 — 꺼 둔 줄 모르면 "야영장이 왜 안 채워지지" 가 된다.
                log.info("야영장 풀 배치가 꺼져 있거나 배치 한도를 넘겨 건너뜁니다");
                return;
            }
            LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
            if (batchRunRepository.hasRunSince(BATCH_NAME, now.minus(RUN_INTERVAL))) {
                log.info("야영장 풀을 최근 {}일 안에 이미 받아 갱신을 건너뜁니다", RUN_INTERVAL.toDays());
                return;
            }
            RefreshOutcome outcome = refresh();
            if (outcome.complete() && outcome.saved() > 0) {
                batchRunRepository.markStarted(BATCH_NAME, now);
            }
        });
    }

    /**
     * 한 회차의 결과.
     *
     * @param saved 저장한 건수
     * @param complete 조회가 온전했나 — 거짓이면 사라진 것 정리를 건너뛴다
     */
    public record RefreshOutcome(int saved, boolean complete) {

        private static final RefreshOutcome NOTHING = new RefreshOutcome(0, false);

        /** 이번 회차는 없던 일이다 — 마커도 남기지 않는다. */
        static RefreshOutcome nothing() {
            return NOTHING;
        }
    }

    /**
     * 전국 야영장을 받아 우리 89곳 것만 저장한다.
     *
     * @return 저장 건수와 회차 완결성
     */
    public RefreshOutcome refresh() {
        // **초 단위로 자른다.** fetched_at 이 DATETIME(소수점 없음)이라, 나노초가 붙은 값을 넣으면
        // MySQL 이 반올림하거나 버린다. 그 결과가 저장값보다 커지는 순간 아래 정리가 **방금 넣은
        // 야영장을 지운다** — 실행 시각의 밀리초에 따라 되기도 안 되기도 하는, 되돌릴 수 없는 손실이다.
        return refresh(LocalDateTime.now(SERVICE_ZONE).truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * 회차 시각을 지정해 받는다.
     *
     * <p><b>시각이 인자인 이유</b>는 그것이 곧 "이번 회차" 의 표식이기 때문이다. 이 값으로 저장하고
     * 이 값보다 오래된 행을 지우므로, 두 회차가 같은 초에 돌면 뒤 회차가 앞 회차를 못 걷어낸다.
     * 운영은 25일 간격이라 닿지 않는 경계지만, 테스트가 시계에 기대지 않으려면 열려 있어야 한다.
     */
    public RefreshOutcome refresh(LocalDateTime fetchedAt) {
        List<Region> regions = regionQuery.all();
        if (regions.isEmpty()) {
            log.info("야영장 풀 — 지역 마스터가 비어 있어 건너뜁니다");
            return RefreshOutcome.nothing();
        }
        RegionNameMatcher matcher = RegionNameMatcher.from(regions);

        GoCampsiteResult received;
        try {
            received = goCampingClient.findAll(TOTAL_DEADLINE);
        } catch (RuntimeException e) {
            // 조회가 깨지면 이번 회차는 없던 일이다. 기존 값을 덮지 않으므로 화면은 그대로다.
            log.warn("야영장 풀 조회 실패 — 이번 회차를 건너뜁니다 cause={}", RootCause.label(e));
            return RefreshOutcome.nothing();
        }
        if (received.items().isEmpty()) {
            // 키가 없거나(로컬) 결과가 비었다. 빈 결과로 정리를 돌리면 있는 야영장을 전부 지운다.
            log.info("야영장 풀 — 받은 야영장이 없어 이번 회차를 건너뜁니다 전체={}", received.totalCount());
            return RefreshOutcome.nothing();
        }
        return save(received.items(), matcher, fetchedAt);
    }

    /**
     * 받은 것을 우리 지역에 붙여 저장한다.
     *
     * <p><b>못 붙인 것을 센다.</b> 전국 3,115건 중 우리 89곳 밖이 대부분이라 그 자체는 정상이지만,
     * 붙은 것이 0이면 지역명 매칭이 깨졌다는 신호다.
     */
    private RefreshOutcome save(List<GoCampsite> collected, RegionNameMatcher matcher, LocalDateTime fetchedAt) {
        List<CampingPlace> ours = new ArrayList<>();
        int ambiguous = 0;
        int unusable = 0;
        for (GoCampsite campsite : collected) {
            // **주소 전체를 넘긴다.** 시군구명만으로는 같은 이름이 둘인 곳(서구·고성군)을 못 가른다 —
            // 강원 고성과 경남 고성은 완전히 다른 곳이라, 한쪽에 몰아넣으면 여행자가 엉뚱한 데로 간다(#502).
            Long regionId = matcher.match(campsite.address()).orElse(null);
            if (regionId == null) {
                if (matcher.knowsName(campsite.sigunguName())) {
                    // 이름은 우리 안에 있는데 시도를 못 읽어 못 갈랐다 — 버린다. 조용히 틀린 지역에
                    // 붙이는 것보다 안 붙이는 편이 낫다.
                    ambiguous++;
                }
                continue; // 우리 89곳 밖 — 대부분이 여기다
            }
            // **어댑터가 이미 걸렀지만 여기서 다시 묻는다.** 중복이 아니라 방어다 — 좌표가 없는 행
            // 하나를 toPlace 로 넘기면 엔티티 불변식이 예외를 던져 **그달 적재가 통째로 실패한다**.
            // port 는 인터페이스라 구현이 바뀔 수 있고, 그 한 건 때문에 회차 전체를 잃을 이유가 없다.
            if (!campsite.isUsable()) {
                unusable++;
                continue;
            }
            ours.add(campsite.toPlace(regionId, fetchedAt));
        }

        if (ours.isEmpty()) {
            // 빈 결과를 성공으로 남기지 않는다 — 다음 회차에 다시 받게 한다.
            //
            // **"못 붙임" 과 "못 씀" 을 갈라 적는다.** 둘을 뭉뚱그리면 원인을 반대로 짚는다 — 앞은
            // 지역명 매칭이 깨진 것이고, 뒤는 원본이 휴장·좌표없음으로 채워진 것이다.
            log.warn("야영장 풀 — 받은 {}건 중 저장할 것이 없습니다 (못씀={}건). 지역명 매칭을 확인하세요",
                    collected.size(), unusable);
            return RefreshOutcome.nothing();
        }

        int saved = campingPlaceRepository.upsertAll(ours);
        int removed = campingPlaceRepository.deleteFetchedBefore(fetchedAt);
        long withPhoto = ours.stream().filter(CampingPlace::hasPhoto).count();
        // **사진 수를 함께 남긴다.** 이 표를 들여온 이유가 사진이라, 건수만 보면 값어치가 줄어드는 것을
        // 놓친다 — 원본이 사진을 빼기 시작해도 총 건수는 그대로다.
        log.info("야영장 풀 저장 완료 받은건수={} 우리지역={}건 사진있음={}건 저장={}건 못씀={}건 사라짐정리={}건",
                collected.size(), ours.size(), withPhoto, saved, unusable, removed);
        if (ambiguous > 0) {
            // 조용히 버리지 않는다 — 이 수가 크면 시도 표기 별칭이 모자란 것이다(#502).
            log.warn("야영장 {}건은 시군구명이 우리 안에 있으나 시도를 못 읽어 버렸습니다 — 시도 별칭을 확인하세요",
                    ambiguous);
        }
        return new RefreshOutcome(saved, true);
    }
}
