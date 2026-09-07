package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.RegionVisitorDaily;
import com.offway.core.trip.domain.VisitorType;
import com.offway.core.trip.repository.RegionVisitorDailyRepository;
import com.offway.core.trip.domain.RegionVisitMetrics;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추세를 재는 두 창이 <b>같은 계절을 담는가</b>(#487).
 *
 * <p>원본은 완결된 달만 발행하는데 적재는 그 달 도중까지 들어온다. 그래서 최근 창은 늘 그 달 며칠에서
 * 끊기는데, 작년 창까지 달 경계로 자르면 <b>작년에만 마지막 달이 통째로</b> 들어간다.
 *
 * <p>일평균으로 견주니 날 수 차이는 보정된다. 보정되지 않는 것은 <b>어느 달이 얼마나 섞였는지</b>다 —
 * 그 달이 성수기면 작년 쪽 일평균만 부풀어 모든 지역이 마이너스로 기운다. 운영 실측에서 실제로 89곳이
 * 전부 그랬다.
 */
@SpringBootTest
@Transactional
class RegionVisitTrendWindowIntegrationTest {

    /** 적재가 이 달 6일에서 끊긴 상황 — 원본이 아직 이 달을 발행하지 않았다. */
    private static final LocalDate LATEST = LocalDate.of(2099, 8, 6);

    /** 비수기 하루치. */
    private static final double OFF_SEASON = 100;

    /** 성수기 하루치 — 8월이다. 이 차이가 창을 어긋나게 잘랐을 때 드러난다. */
    private static final double PEAK_SEASON = 200;

    @Autowired
    private RegionVisitMetricsService metricsService;

    @Autowired
    private RegionVisitorDailyRepository dailyRepository;

    @Autowired
    private RegionRepository regionRepository;

    /**
     * <b>두 해가 똑같으면 증감은 0이다.</b>
     *
     * <p>같은 값을 두 해에 넣었으므로 어떤 창으로 잘라도 "그대로" 여야 한다. 창이 어긋나 있으면 작년
     * 쪽에만 성수기 8월이 25일 더 들어가 일평균이 부풀고, <b>변화가 없는데 감소로 나온다.</b>
     *
     * <p>이 단언이 곧 회귀 방지다 — 창을 다시 달 경계로 되돌리면 여기가 음수로 깨진다.
     */
    @Test
    void 두_해가_같으면_마지막_달이_잘려도_증감이_0이다() {
        String code = 어느지역코드();
        List<RegionVisitorDaily> rows = new ArrayList<>();
        // 작년: 6/1 ~ 8/31 (원본이 완결한 달들)
        채운다(rows, code, LocalDate.of(2098, 6, 1), LocalDate.of(2098, 8, 31));
        // 올해: 6/1 ~ 8/6 (아직 8월이 안 끝났다)
        채운다(rows, code, LocalDate.of(2099, 6, 1), LATEST);
        dailyRepository.insertIfAbsent(rows);
        metricsService.evictCache();

        RegionVisitMetrics metrics = metricsService.of(code);

        assertNotNull(metrics.trend(), "두 창 모두 표본이 충분하므로 추세가 나와야 한다");
        assertEquals(0, metrics.trend().percent(),
                "두 해가 같은데 감소로 나오면 작년 창에만 성수기가 더 담긴 것이다");
    }

    /** 그 지역에서 실제로 쓰는 시군구 코드 하나. */
    private String 어느지역코드() {
        return regionRepository.findAll().stream()
                .map(Region::getLegalCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("법정 코드를 가진 지역이 없어 성립하지 않는다"));
    }

    /**
     * 하루치를 유형별로 넣는다 — 관광객은 외지인·외국인이고 현지인은 집계에서 빠진다.
     *
     * <p>8월만 값을 올린다. 성수기가 어느 창에 얼마나 담기는지가 이 테스트의 전부다.
     */
    private void 채운다(List<RegionVisitorDaily> rows, String code, LocalDate from, LocalDate to) {
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            double perType = (date.getMonthValue() == 8 ? PEAK_SEASON : OFF_SEASON) / 2;
            rows.add(RegionVisitorDaily.builder()
                    .signguCode(code).baseDate(date).visitorType(VisitorType.DOMESTIC).visitorCount(perType).build());
            rows.add(RegionVisitorDaily.builder()
                    .signguCode(code).baseDate(date).visitorType(VisitorType.FOREIGN).visitorCount(perType).build());
            // 현지인은 관광객이 아니다. 넣어 두는 이유는 이 값이 집계에 안 섞이는지도 함께 보기 위해서다.
            rows.add(RegionVisitorDaily.builder()
                    .signguCode(code).baseDate(date).visitorType(VisitorType.LOCAL).visitorCount(9_999).build());
        }
    }
}
