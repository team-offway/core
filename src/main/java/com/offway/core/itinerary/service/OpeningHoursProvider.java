package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.service.dto.SlotHours;
import com.offway.core.leave.service.HolidayProvider;
import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.domain.OpeningStatus;
import com.offway.core.trip.repository.PoiIntroRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 코스에 실린 장소들의 운영시간을 <b>DB 에서만</b> 읽고, 여행일이 오늘이면 <b>지금 여는지</b>까지 판정한다
 * (#157·#189).
 *
 * <p>요청 경로에서 외부를 부르지 않는다. 슬롯마다 {@code detailIntro2} 를 부르면 코스 하나에 20건이 넘어
 * 관광정보 한도가 코스 40개면 마른다. 받아 두는 일은 배치({@code PoiIntroRefreshService})가 한다.
 *
 * <p><b>판정은 오늘 여행 중일 때만.</b> 지금 시각으로 내리는 판정이라 다음 주 코스에 붙이면 사용자가
 * 여행일 상태로 읽는다 — 없는 것보다 나쁘다.
 */
@Component
@RequiredArgsConstructor
public class OpeningHoursProvider {

    /** "오늘" 판정은 KST — 사용자가 서 있는 시간대다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PoiIntroRepository poiIntroRepository;
    private final HolidayProvider holidayProvider;

    /**
     * 코스 전체 슬롯의 운영 정보를 한 번의 조회로 가져온다 — 슬롯마다 읽으면 N+1 이 된다.
     *
     * <p>공휴일 조회도 <b>코스당 한 번</b>이다. 슬롯마다 물으면 같은 날짜를 스무 번 묻는다.
     */
    public Map<String, SlotHours> forCourse(Course course) {
        List<String> contentIds = course.getDays().stream()
                .map(DaySchedule::getSlots)
                .flatMap(List::stream)
                .map(Slot::getPoiContentId)
                .distinct()
                .toList();
        Map<String, OpeningHours> stored = poiIntroRepository.findByContentIds(contentIds);
        if (stored.isEmpty()) {
            return Map.of();
        }

        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        boolean judge = course.covers(now.toLocalDate());
        boolean holiday = judge && isHoliday(now.toLocalDate());

        Map<String, SlotHours> result = new HashMap<>();
        stored.forEach((contentId, hours) ->
                result.put(contentId, new SlotHours(hours,
                        judge ? hours.statusAt(now, holiday) : OpeningStatus.UNKNOWN)));
        return result;
    }

    /** 공휴일 조회가 실패해도 코스는 나가야 한다 — 예외 조항 판정만 보수적으로 간다(휴무로 본다). */
    private boolean isHoliday(LocalDate today) {
        try {
            return holidayProvider.holidaysWithin(today, today).contains(today);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
