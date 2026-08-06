package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.region.domain.Region;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.weather.service.WeatherService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 코스의 Day 별 날씨를 붙인다 — 생성 경로와 저장 코스 조회가 함께 쓴다(#169).
 *
 * <p><b>왜 따로 뺐나.</b> 생성 응답에는 날씨가 실리는데 같은 코스를 저장한 뒤 상세로 조회하면 비어 있었다. 조회
 * 경로가 이 조립을 안 했기 때문이다. 로직을 한곳에 두면 한쪽만 고쳐지는 일이 없다.
 *
 * <p><b>외부 호출이라 트랜잭션 밖에서 불러야 한다.</b> 기상청은 read-timeout 이 길어 트랜잭션 안에 넣으면 DB
 * 커넥션을 오래 잡는다(영속성 규약). 이 컴포넌트는 트랜잭션을 열지 않으므로 호출자가 경계를 지켜야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseWeatherProvider {

    private final WeatherService weatherService;

    /**
     * Day 별 날씨. 예보가 없는 Day 는 <b>키 자체를 넣지 않는다</b> — 화면이 Day 마다 독립적으로 판단한다.
     *
     * <p>날짜를 모르는 코스(저장 시 날짜 미입력)는 물어볼 기준이 없어 통째로 빈다.
     *
     * @param anchor 조회 좌표. 생성은 코스 중심(hub), 저장 코스는 슬롯 중심을 쓴다
     * @param region 시도·시군구는 중기예보 구역을 고르는 데 쓴다(#129). 없으면 나흘 뒤부터 답하지 못한다
     */
    public Map<Integer, DailyWeather> byDay(Course course, Region region, Coordinate anchor) {
        if (course.getTravelDate() == null || anchor == null) {
            return Map.of();
        }
        Map<Integer, DailyWeather> byDay = new LinkedHashMap<>();
        for (DaySchedule schedule : course.getDays()) {
            // 조회는 달력 오프셋으로 — 첫날이 빠진 코스에서 표시 번호로 날짜를 세면 하루 앞 예보가 붙는다(#159).
            // 키는 표시 번호를 그대로 쓴다. 응답의 Day 와 짝이 맞아야 한다.
            int day = schedule.getDayNumber();
            weatherService.dailyWeather(
                            anchor.lat(), anchor.lng(),
                            region == null ? null : region.getSido(),
                            region == null ? null : region.getSigungu(),
                            course.dateOf(schedule))
                    .ifPresent(weather -> byDay.put(day, weather));
        }
        return byDay;
    }
}
