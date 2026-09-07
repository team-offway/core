package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiBatchPolicy;
import com.offway.core.common.logging.RootCause;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceNameKey;
import com.offway.core.trip.domain.RelatedAttraction;
import com.offway.core.trip.infrastructure.datalab.RelatedAttractionClient;
import com.offway.core.trip.infrastructure.datalab.dto.RelatedAttractionItem;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.repository.RelatedAttractionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 연관 관광지를 받아 <b>좌표를 붙여</b> 적재한다(#186).
 *
 * <h2>이 배치의 절반은 좌표 조인이다</h2>
 *
 * <p>원본이 좌표를 안 주므로 인허가 장소(#144)와 이름으로 잇는다({@link PlaceNameKey}). <b>못 이은
 * 것은 담지 않는다</b> — 좌표 없는 후보를 저장하면 조회하는 쪽이 매번 걸러야 하고, 한 군데만
 * 빠뜨려도 동선이 깨진다.
 *
 * <p>매칭률을 <b>지역마다 남긴다.</b> 실측(공주시)에서 음식 75곳 중 54곳(72%)이 붙었는데, 이 값이
 * 지역마다 다르고 이름 표기 규칙이 바뀌면 조용히 떨어진다.
 *
 * <h2>주기를 원본에 맞춘다</h2>
 *
 * <p>월 단위 발행이라 매월 1일 cron 이 갱신을 소유하고, 부팅 확인이 그 사이 공백만 메운다. 그 위에
 * <b>지역별 기준월 마커</b>가 얹힌다 — 목표월(또는 그 이후) 자료를 이미 가진 지역은 외부를 아예 안
 * 부른다({@code HubAttractionRefreshService} 와 같은 판단).
 *
 * <h2>순차로 부른다</h2>
 *
 * <p>병렬로 던지면 초당 호출이 폭증해 429 를 맞는다(#191). 월 1회 배경 작업이라 89건을 순서대로 도는
 * 시간은 아무도 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelatedAttractionRefreshService {

    private static final String SERVICE_ZONE_ID = "Asia/Seoul";
    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 매월 1일 새벽 4시 30분.
     *
     * <p>중심관광지(04:00)가 먼저 돌아야 이 값이 가리키는 중심이 자리를 잡는다. 30분이면 89곳 순차
     * 조회가 끝난다.
     */
    private static final String MONTHLY_AT_DAWN = "0 30 4 1 * *";

    /** 부팅 뒤 확인 — 배포가 잦아 cron 을 놓칠 수 있다. 지역별 마커가 중복 호출을 막는다. */
    private static final String BOOT_CHECK_DELAY = "PT360S";

    private static final String BOOT_CHECK_INTERVAL = "P7D";

    static final String BATCH_NAME = "related-attraction-refresh";

    private static final Caller CALLER = Caller.of("연관관광지배치");

    /**
     * 지역당 받을 건수.
     *
     * <p>원본이 "유형별 최대 각 50위" 를 주고 중심관광지가 여럿이라, 실측(공주시)에서 300건이 왔다.
     * {@value} 면 그보다 넉넉하고, 지역 밖·좌표 미매칭이 빠지면 실제 저장은 그 절반 남짓이다.
     */
    private static final int ROWS_PER_REGION = 500;

    /**
     * 원본 발행이 밀렸을 때 되돌아볼 개월 수.
     *
     * <p>데이터랩은 완결된 달만 발행하는데 그 시점이 지역마다 다르다. 한 달만 보고 포기하면 발행이
     * 며칠 밀린 지역이 통째로 빈다.
     */
    private static final int MAX_MONTHS_BACK = 3;

    private final RelatedAttractionClient relatedAttractionClient;
    private final RelatedAttractionRepository relatedAttractionRepository;
    private final LicensedPlaceRepository licensedPlaceRepository;
    private final RegionQuery regionQuery;
    private final ExternalApiBatchPolicy batchPolicy;
    private final BatchRunRepository batchRunRepository;

    /**
     * <h2>지역별 마커만으로는 한도를 못 막는다 — 하루 한 번으로 끊는다</h2>
     *
     * <p>건너뛰기 판정이 <b>"그 지역이 목표월 자료를 가졌나"</b> 였다. 원본이 목표월을 아직 발행하지
     * 않으면 되짚기가 이전 달을 다시 저장하는데, 저장된 달은 여전히 목표월보다 앞이라 <b>다음 회차에 또
     * 걸린다.</b> 조건이 사실상 참이 되지 않는다.
     *
     * <p>실측(2026-09-07). 89곳이 6·7월 자료를 갖고 있는데 목표가 8월이라 전부 낡음으로 걸렸고, 회차마다
     * <b>194콜</b>(73곳×2 + 16곳×3)을 태우며 <b>같은 7월 행을 다시 저장</b>했다. {@code fixedDelay} 는
     * 재배포마다 처음부터 다시 세므로(#226·#231) 배포가 잦은 날 네 번 돌아 하루 <b>780콜 · 한도의 78%</b>
     * 가 됐다. 그렇게 늘어난 자료는 <b>0건</b>이다.
     *
     * <p>{@code HubAttractionRefreshService} 가 <b>같은 자리에서 같은 실수</b>를 했고(#337) 이미 해법을
     * 갖고 있다 — 하루 한 번으로 끊는 것이다. 원본이 월 단위 발행이라 하루 한 번이면 넉넉하다.
     *
     * <p><b>결과가 아니라 실행을 기록한다.</b> 적재 결과로 판정하면 전부 실패한 날에는 아무것도 안 써져
     * 다음 부팅이 또 쏜다. 확인과 기록을 한 문장으로 묶는 것도 그쪽과 같은 이유다 — 트리거가 둘이라
     * "확인 → 호출 → 기록" 사이의 창으로 다른 트리거가 들어오면 같은 날 두 번 쏜다.
     *
     * <p>대가는 있다. 그날 실패한 지역은 그날 안에 다시 시도하지 않는다. 이전 값이 남아 화면은 유지되고
     * 처음부터 빈 지역만 하루를 기다리는데, 한도를 태워 <b>모두</b>가 실패하는 것보다 낫다.
     */
    @Scheduled(cron = MONTHLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            if (!batchPolicy.batchMayCall(BATCH_NAME, ExternalApi.TOUR_DATA_LAB)) {
                // 조용히 넘기지 않는다 — 꺼 둔 줄 모르면 "연관 관광지가 왜 안 채워지지" 가 된다.
                log.info("연관 관광지 배치가 꺼져 있거나 배치 한도를 넘겨 건너뜁니다");
                return;
            }
            LocalDate today = LocalDate.now(SERVICE_ZONE);
            if (!batchRunRepository.tryStartOn(BATCH_NAME, today, LocalDateTime.now(SERVICE_ZONE))) {
                log.info("연관 관광지를 오늘 이미 돌렸거나 다른 트리거가 선점해 갱신을 건너뜁니다 date={}", today);
                return;
            }
            // 선점이 곧 기록이다 — 아래가 실패해도 시각이 남아 같은 날 재부팅이 다시 쏘지 않는다.
            refresh(newestPossibleMonth());
        });
    }

    /**
     * 89곳을 순차로 돌며 그 지역 연관 관광지를 채운다.
     *
     * <p><b>지역 하나의 실패가 나머지를 막지 않는다.</b> 외부 실패든 우리 시드 문제든 그 지역만
     * 건너뛴다 — 89곳 루프가 통째로 멎으면 그달 갱신이 통째로 없던 일이 된다.
     *
     * @param target 받고 싶은 기준월. 그보다 새 자료를 이미 가진 지역은 건너뛴다
     * @return 이 회차로 채운 지역 수
     */
    public int refresh(YearMonth target) {
        List<Region> regions = regionQuery.all();
        if (regions.isEmpty()) {
            log.info("연관 관광지 — 지역 마스터가 비어 있어 건너뜁니다");
            return 0;
        }

        int filled = 0;
        int skipped = 0;
        int failed = 0;
        int totalMatched = 0;
        int totalDroppedNoCoordinate = 0;
        for (Region region : regions) {
            if (hasFresh(region, target)) {
                skipped++;
                continue;
            }
            try {
                Filled result = fillRegion(region, target);
                // **매칭 실패는 저장 여부와 무관하게 센다.** 한 지역이 통째로 못 이어지면 saved 가 0인데,
                // 그것까지 분모에서 빼면 매칭률이 실제보다 높게 찍힌다 — 가장 나쁜 지역이 통계에서 사라진다.
                totalDroppedNoCoordinate += result.droppedNoCoordinate();
                if (result.saved() > 0) {
                    filled++;
                    totalMatched += result.saved();
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("연관 관광지 적재 실패 — 그 지역만 건너뜁니다 regionId={} cause={}",
                        region.getId(), RootCause.label(e));
            }
        }

        // **매칭률을 남긴다.** 이름 표기 규칙이 바뀌면 조용히 떨어지는 값이라, 회차마다 보이게 한다.
        int attempted = totalMatched + totalDroppedNoCoordinate;
        log.info("연관 관광지 적재 완료 target={} 채움={}곳 건너뜀={}곳 실패={}곳 저장={}건 좌표없어제외={}건 매칭률={}%",
                target, filled, skipped, failed, totalMatched, totalDroppedNoCoordinate,
                attempted == 0 ? 0 : totalMatched * 100 / attempted);
        return filled;
    }

    /** 이미 목표월(또는 그 이후) 자료를 가졌나 — 지역별로 판정한다. */
    private boolean hasFresh(Region region, YearMonth target) {
        return relatedAttractionRepository.latestBaseMonth(region.getId())
                .filter(held -> !held.isBefore(target))
                .isPresent();
    }

    /**
     * 한 지역을 채운다 — 발행이 밀렸으면 이전 달로 물러선다.
     *
     * <p>데이터랩은 완결된 달만 발행하고 그 시점이 지역마다 다르다. 한 달만 보고 포기하면 며칠 밀린
     * 지역이 통째로 빈다.
     */
    private Filled fillRegion(Region region, YearMonth target) {
        Map<PlaceNameKey, LicensedPlace> byName = nameIndex(region.getId());
        YearMonth month = target;
        for (int back = 0; back < MAX_MONTHS_BACK; back++, month = month.minusMonths(1)) {
            List<RelatedAttractionItem> items = relatedAttractionClient.findByRegion(
                    region.getLegalCode(), region.getSigungu(), month, ROWS_PER_REGION);
            if (items.isEmpty()) {
                continue; // 그 달 미발행 — 이전 달로 물러선다
            }
            Filled filled = toEntities(region, month, items, byName);
            relatedAttractionRepository.replaceRegion(region.getId(), month, filled.rows());
            return filled;
        }
        log.debug("연관 관광지 — 최근 {}개월이 모두 비었습니다 regionId={}", MAX_MONTHS_BACK, region.getId());
        return Filled.nothing();
    }

    /**
     * 이름 → 인허가 장소. <b>같은 열쇠가 여럿이면 먼저 온 것을 남긴다.</b>
     *
     * <p>정규화 후 겹치는 상호가 있다("○○식당" 이 한 지역에 둘). 어느 쪽을 골라도 근거가 없어서,
     * 적어도 <b>같은 입력이면 같은 답</b>이 나오게 첫 번째로 고정한다 — 회차마다 좌표가 바뀌면
     * 코스가 이유 없이 달라진다.
     */
    private Map<PlaceNameKey, LicensedPlace> nameIndex(long regionId) {
        Map<PlaceNameKey, LicensedPlace> index = new HashMap<>();
        for (LicensedPlace place : licensedPlaceRepository.findAllInRegion(regionId)) {
            PlaceNameKey.of(place.getName()).ifPresent(key -> index.putIfAbsent(key, place));
        }
        return index;
    }

    /** 좌표를 붙여 엔티티로. <b>못 붙인 것은 버린다</b> — 좌표 없는 후보는 동선에 못 올린다. */
    private Filled toEntities(
            Region region, YearMonth month,
            List<RelatedAttractionItem> items, Map<PlaceNameKey, LicensedPlace> byName) {
        List<RelatedAttraction> rows = new ArrayList<>();
        int dropped = 0;
        for (RelatedAttractionItem item : items) {
            Optional<LicensedPlace> matched = PlaceNameKey.of(item.name()).map(byName::get);
            if (matched.isEmpty()) {
                dropped++;
                continue;
            }
            LicensedPlace place = matched.get();
            rows.add(RelatedAttraction.builder()
                    .regionId(region.getId())
                    .baseMonth(month)
                    .hubCode(item.hubCode())
                    .hubName(item.hubName())
                    .relatedCode(item.code())
                    .relatedName(item.name())
                    .relatedRank(item.rank())
                    .categoryLarge(item.categoryLarge())
                    .categoryMedium(item.categoryMedium())
                    .licensedPlaceId(place.getId())
                    .lat(place.getLat())
                    .lng(place.getLng())
                    .build());
        }
        return new Filled(rows, dropped);
    }

    /** 원본이 완결된 달만 발행하므로 지난달이 가장 새로울 수 있는 값이다. */
    private static YearMonth newestPossibleMonth() {
        return YearMonth.from(LocalDate.now(SERVICE_ZONE)).minusMonths(1);
    }

    /**
     * 한 지역의 적재 결과.
     *
     * @param rows 좌표까지 붙은 것
     * @param droppedNoCoordinate 이름을 못 이어 버린 것 — 매칭률의 분모가 된다
     */
    private record Filled(List<RelatedAttraction> rows, int droppedNoCoordinate) {

        private static Filled nothing() {
            return new Filled(List.of(), 0);
        }

        private int saved() {
            return rows.size();
        }
    }
}
