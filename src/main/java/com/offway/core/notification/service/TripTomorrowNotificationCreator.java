package com.offway.core.notification.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.service.dto.PushTarget;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 여행 전날 알림의 <b>대상을 고른다</b>(#269).
 *
 * <p>만드는 일 자체는 {@link CourseNotificationWriter} 가 한다 — 여행 종료 다음 날 배치(#302)와 같은 일이라
 * 한 곳에 뒀다. 여기 남는 것은 "누구에게 보낼 것인가" 하나다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripTomorrowNotificationCreator {

    private static final String LABEL = "여행 전날 알림";

    private final CourseRepository courseRepository;
    private final CourseNotificationWriter writer;

    /**
     * 기준일 다음 날 떠나는 코스에 알림을 만든다.
     *
     * <p>기준일을 인자로 받는 이유는 <b>"내일" 이 언제인지를 호출자가 정할 수 있어야</b> 하기 때문이다.
     * 스케줄러는 오늘을 넘기고, 테스트는 고정된 날짜를 넘긴다.
     *
     * @return 이 실행으로 새로 만들어진 알림들
     */
    public List<PushTarget> createForTripsStartingTomorrow(LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        List<Course> departing = courseRepository.findByTravelDate(tomorrow);
        return writer.create(departing, NotificationType.TRIP_TOMORROW, LABEL + " travelDate=" + tomorrow);
    }
}
