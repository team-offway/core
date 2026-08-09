package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.region.domain.Region;
import com.offway.core.weather.domain.AirQuality;
import com.offway.core.weather.service.AirQualityService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 코스에 실시간 대기질을 붙인다 — <b>오늘 여행 중인 코스에만</b>.
 *
 * <p><b>왜 오늘만인가.</b> 에어코리아 값은 예보가 아니라 지금 이 순간의 측정치다. 다음 주 코스에 붙이면
 * 사용자가 여행일 공기질로 읽는데, 그건 없는 것보다 나쁘다 — 틀린 값을 근거로 짐을 싸게 된다.
 *
 * <p><b>왜 홈에서 옮겨 왔나.</b> 예전에는 홈 카드마다 시도별 대기질을 요청 경로에서 채웠다. 에어코리아가
 * 시도 몇 곳에서 느려지면(실측 2026-08-10: 17곳 중 4곳이 5~10초) 그 지연을 사용자가 그대로 물어,
 * 홈이 24초 걸린 요청이 실제로 남아 있다. 대기질이 필요한 자리는 "지금 그 지역에 가 있는" 화면뿐이라
 * 코스로 옮기면 그 경로가 통째로 사라진다.
 *
 * <p>생성 경로와 저장 코스 조회가 함께 쓴다 — 한쪽만 붙으면 저장한 코스에서 값이 사라진다(#169 와 같은 실수).
 *
 * <p><b>외부 호출이라 트랜잭션 밖에서 불러야 한다</b>(영속성 규약). 이 컴포넌트는 트랜잭션을 열지 않으므로
 * 호출자가 경계를 지킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseAirQualityProvider {

    /** "오늘" 은 KST 기준 — 여행 날짜가 국내 날짜라 서버 타임존을 따라가면 자정 근처에서 하루가 어긋난다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AirQualityService airQualityService;

    /**
     * 코스 지역의 실시간 대기질. 오늘 여행 중이 아니거나, 지역을 모르거나, 조회가 안 되면 null.
     *
     * @param region 시도로 조회한다. 대기질은 시도 단위 발표라 시군구는 쓰지 않는다
     */
    public AirQuality of(Course course, Region region) {
        if (region == null) {
            return null;
        }
        if (!course.covers(LocalDate.now(SERVICE_ZONE))) {
            return null;
        }
        return airQualityService.byRegionSido(region.getSido()).orElse(null);
    }
}
