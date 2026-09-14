package com.offway.core.trip.service;

import com.offway.core.trip.domain.AttractionCrowdForecast;
import com.offway.core.trip.domain.CrowdChip;
import com.offway.core.trip.domain.RegionVisitMetrics;
import com.offway.core.trip.repository.AttractionCrowdForecastRepository;
import com.offway.core.trip.service.dto.CourseCrowd;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 혼잡 칩을 낸다(#565) — 장소별 집중률 예보가 있으면 그것으로, 없으면 지역 요일계수로.
 *
 * <h2>코스 한 건을 한 번에 받는다</h2>
 *
 * <p>슬롯마다 물으면 코스 하나에 질의가 슬롯 수만큼 나간다. 지역과 날짜가 코스 단위로 정해지므로
 * <b>지역 하나 × 그 코스의 날짜들</b>을 한 번 읽어 이름으로 꺼내 쓴다.
 *
 * <h2>이름으로 맞춘다</h2>
 *
 * <p>이 API 는 콘텐츠 ID 를 안 주고 관광지명만 준다. 실측(2026-09-14, 표본 6곳)에서 정규화 없는 정확
 * 일치가 느슨 일치와 같았다 — 같은 공사 데이터라 표기가 어긋나지 않는다. 정규화를 넣으면 다른 장소를
 * 같은 것으로 볼 위험만 는다.
 *
 * <p><b>맞는 이름이 없으면 폴백으로 내려간다.</b> 볼거리 기준 매칭률이 53% 라(실측 9곳 348개 중 186개),
 * 절반쯤은 이 경로를 탄다.
 *
 * <p>외부를 부르지 않는다 — 배치가 받아 둔 것만 읽는다(#157).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttractionCrowdService {

    private final AttractionCrowdForecastRepository forecastRepository;
    private final RegionVisitMetricsService regionVisitMetricsService;

    /**
     * 코스 한 건이 쓸 혼잡 칩을 미리 뽑는다.
     *
     * @param regionId 코스의 지역
     * @param legalCode 그 지역의 법정 시군구코드 — 폴백이 지명이 아니라 코드로 찾는다
     * @param dates 코스가 걸친 날짜들. 비어 있으면(저장 시 날짜를 안 넣은 코스) 빈 결과다
     */
    public CourseCrowd forCourse(long regionId, String legalCode, Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            // 날짜를 모르면 "그 날 붐비나" 에 답할 수 없다. 지어내지 않는다.
            return CourseCrowd.empty();
        }
        Map<CourseCrowd.Key, CrowdChip> byPlace = new HashMap<>();
        for (AttractionCrowdForecast forecast : forecastRepository.findByRegionAndDates(regionId, dates)) {
            CrowdChip.ofForecast(forecast.getRate()).ifPresent(chip -> byPlace.put(
                    new CourseCrowd.Key(forecast.getAttractionName(), forecast.getBaseDate()), chip));
        }

        // 폴백은 지역 단위라 날짜별로 한 번씩만 구하면 된다.
        RegionVisitMetrics metrics = regionVisitMetricsService.of(legalCode);
        Map<LocalDate, CrowdChip> byRegionDate = new HashMap<>();
        for (LocalDate date : Set.copyOf(dates)) {
            metrics.crowdOn(date).ifPresent(chip -> byRegionDate.put(date, chip));
        }
        return new CourseCrowd(Map.copyOf(byPlace), Map.copyOf(byRegionDate));
    }
}
