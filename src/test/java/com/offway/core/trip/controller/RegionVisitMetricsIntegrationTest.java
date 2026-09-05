package com.offway.core.trip.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.RegionVisitorDaily;
import com.offway.core.trip.domain.VisitorType;
import com.offway.core.trip.repository.RegionVisitorDailyRepository;
import com.offway.core.trip.service.RegionVisitMetricsService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쌓아 둔 일별 방문자가 <b>화면까지 도달하는가</b>(#394).
 *
 * <p>단위 테스트는 계산만 본다. 여기서 잠그는 것은 <b>적재 → 집계 → 응답</b> 이 이어져 있는가다 —
 * 그 사이 어디가 끊겨도 계산은 초록인 채로 화면만 빈다.
 *
 * <p><b>일별 행을 리포지토리로 직접 심는다.</b> 적재 배치를 태우면 외부 stub 이 필요하고 컨텍스트가
 * 하나 더 뜨는데, 이 테스트가 보려는 것은 적재가 아니라 응답 모양이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class RegionVisitMetricsIntegrationTest {

    private static final String URL = "/api/v1/regions/{regionId}";

    /** 실제 적재분과 안 섞이게 먼 미래에 둔다 — 기존 통합 테스트가 쓰는 2099 와 같은 방식이다. */
    private static final LocalDate LAST_DAY = LocalDate.of(2099, 12, 31);

    /** 최근 3개월 + 그 작년 3개월을 덮는 길이. 요일 표본(요일당 52일)도 이 안에서 채워진다. */
    private static final LocalDate FIRST_DAY = LocalDate.of(2098, 10, 1);

    /** 추세 구간이 시작되는 날 — 여기부터 방문자가 늘어난 것으로 심는다. */
    private static final LocalDate RISE_FROM = LocalDate.of(2099, 10, 1);

    private static final double BASE = 100;
    private static final double RISEN = 140;

    /** 화요일만 3할 적게 — 나머지 요일 대비 30% 격차를 만든다. */
    private static final double TUESDAY_RATIO = 0.7;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RegionVisitorDailyRepository dailyRepository;

    @Autowired
    private RegionVisitMetricsService regionVisitMetricsService;

    /**
     * <b>이 테스트가 이 작업의 계약이다.</b> 심어 둔 방문 이력에서 한산한 요일과 인기 추세가 함께 나온다.
     *
     * <p>한 데이터로 둘을 다 본다. 값에 요일 비율을 곱하는 방식이라 추세 구간에서도 화요일 비율이
     * 유지되고, 그래서 두 지표가 서로를 흐리지 않는다.
     */
    @Test
    void 쌓아_둔_방문_이력에서_한산한_요일과_추세가_함께_나온다() throws Exception {
        Region region = anyRegion();
        심는다(region.getLegalCode());

        mockMvc.perform(get(URL, region.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitMetrics.quietestDay.dayOfWeek").value("TUESDAY"))
                // 서버가 한글 라벨을 든다 — 클라이언트마다 요일 표기를 따로 만들지 않게.
                .andExpect(jsonPath("$.data.visitMetrics.quietestDay.label").value("화요일"))
                .andExpect(jsonPath("$.data.visitMetrics.quietestDay.percentLessThanOtherDays").value(30))
                .andExpect(jsonPath("$.data.visitMetrics.trend.percent").value(40))
                .andExpect(jsonPath("$.data.visitMetrics.trend.rising").value(true));
    }

    /**
     * <b>출처 표기는 여기서 보지 않는다(의도적 생략).</b>
     *
     * <p>"지표만 실려도 공사가 붙는가" 는 {@code RegionDetailResponseSourcesTest} 가 잠근다. 그쪽은
     * 응답 객체를 직접 조립해 <b>지표 외에 공사 값이 하나도 없는 상태</b>를 만들 수 있다.
     *
     * <p>여기서는 못 만든다. 지역의 소개·대표 사진이 공유 컨텍스트의 상태라, 다른 테스트가 무엇을
     * 남겼느냐에 따라 지표 없이도 공사가 붙는다. 실제로 이 테스트를 단독으로 돌리면 통과하고 전체로
     * 돌리면 앞의 단언이 깨졌다 — <b>그 상태에서 뒤만 단언하면 지표를 통째로 지워도 초록이 된다.</b>
     */
    @Test
    void 지표가_응답에_실린다() throws Exception {
        Region region = anyRegion();
        심는다(region.getLegalCode());

        mockMvc.perform(get(URL, region.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitMetrics.quietestDay").exists())
                .andExpect(jsonPath("$.data.visitMetrics.trend").exists());
    }

    /**
     * <b>재료가 없으면 값이 아니라 null 이 나간다.</b> 필드 자체는 있어서, 앱이 "이 값을 모르는 옛
     * 서버" 와 "아직 못 재는 지역" 을 구분할 필요가 없다.
     */
    @Test
    void 아직_못_재는_지역은_지표가_비어_있다() throws Exception {
        Region region = anyRegion();
        regionVisitMetricsService.evictCache();

        mockMvc.perform(get(URL, region.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitMetrics").exists())
                .andExpect(jsonPath("$.data.visitMetrics.quietestDay").doesNotExist())
                .andExpect(jsonPath("$.data.visitMetrics.trend").doesNotExist());
    }

    /**
     * 방문 이력을 심는다 — {@link #FIRST_DAY} 부터 {@link #LAST_DAY} 까지 매일 한 줄.
     *
     * <p>심은 뒤 캐시를 비운다. 지표 서비스는 기준일이 바뀌었을 때만 다시 계산하는데, 공유 컨텍스트라
     * 앞선 테스트가 같은 기준일로 이미 계산해 뒀을 수 있다.
     */
    private void 심는다(String signguCode) {
        List<RegionVisitorDaily> rows = new ArrayList<>();
        for (LocalDate date = FIRST_DAY; !date.isAfter(LAST_DAY); date = date.plusDays(1)) {
            double base = date.isBefore(RISE_FROM) ? BASE : RISEN;
            double count = date.getDayOfWeek() == DayOfWeek.TUESDAY ? base * TUESDAY_RATIO : base;
            rows.add(RegionVisitorDaily.builder()
                    .signguCode(signguCode)
                    .baseDate(date)
                    // 관광객만 센다 — 거주자를 섞으면 요일 차이가 희석된다.
                    .visitorType(VisitorType.DOMESTIC)
                    .visitorCount(count)
                    .build());
        }
        dailyRepository.insertIfAbsent(rows);
        regionVisitMetricsService.evictCache();
    }

    private Region anyRegion() {
        List<Region> regions = new ArrayList<>(regionRepository.findAll());
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.get(0);
    }
}
