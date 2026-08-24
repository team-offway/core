package com.offway.core.notification.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.service.TripOutcomeService;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.service.dto.PushTarget;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 여행이 끝난 다음 날, 아직 답하지 않은 사람에게 물을 <b>대상을 고른다</b>(#302).
 *
 * <p><b>대상 판정을 여기서 하지 않는다.</b> "답했나·차감했나" 는 {@code itinerary} 가 아는 사실이라 그 도메인의
 * 서비스에 묻는다({@link TripOutcomeService#unansweredTripsEndedOn}). 알림이 {@code trip_outcome}·
 * {@code leave_usage} 를 직접 뒤지면 같은 규칙이 두 도메인에 살게 되고, 갈리는 순간 <b>알림은 갔는데 눌러
 * 들어가면 모달이 안 뜨는</b> 헛걸음이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripAfterNotificationCreator {

    private static final String LABEL = "여행 후기 알림";

    private final TripOutcomeService tripOutcomeService;
    private final CourseNotificationWriter writer;

    /**
     * 기준일 <b>전날</b> 끝난 여행 중 아직 답하지 않은 것에 알림을 만든다.
     *
     * <p>종료 당일이 아니라 다음 날인 이유는 {@code pending} 과 같다 — 종료 당일은 아직 여행 중일 수 있다.
     *
     * @return 이 실행으로 새로 만들어진 알림들
     */
    public List<PushTarget> createForTripsEndedYesterday(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        List<Course> waiting = tripOutcomeService.unansweredTripsEndedOn(yesterday);
        return writer.create(waiting, NotificationType.TRIP_AFTER, LABEL + " endedOn=" + yesterday);
    }
}
