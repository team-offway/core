package com.offway.core.notification.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내일 떠나는 여행을 전날 알린다(#269).
 *
 * <p>{@code #263} 이 알림을 보여주는 자리를 만들었지만 아무도 알림을 만들지 않아 모든 사용자의 목록이
 * 비어 있었다. {@link NotificationType#TRIP_TOMORROW} 를 실제로 만드는 것이 여기다.
 *
 * <p><b>푸시로 보내지는 않는다.</b> 여기까지는 앱 안 알림 목록이 채워지는 데까지고, 저장한 토큰으로
 * 실제 발송하는 것은 후속이다(#270).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripTomorrowNotifier {

    /**
     * 사용자가 이 알림을 받는 시각. <b>이 값이 곧 사용자 경험이다</b> — "전날" 이 몇 시인지가 여기서 정해진다.
     *
     * <p>저녁 8시로 둔다. 짐을 싸는 시간대이면서, 자정 직전처럼 알림이 하루를 넘겨 "내일" 이 어긋날 위험이
     * 없다. 새벽에 보내면 자는 사람을 깨우고, 낮에 보내면 퇴근 전이라 할 수 있는 게 없다.
     */
    private static final String DAILY_AT_EVENING = "0 0 20 * * *";

    /** 서비스 기준 시간대. 여행 날짜는 한국 사용자의 달력 기준이라 서버 로케일에 맡기지 않는다. */
    private static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    private final CourseRepository courseRepository;
    private final NotificationRepository notificationRepository;

    /**
     * 매일 저녁, 내일 떠나는 코스의 주인에게 알림을 만든다.
     *
     * <p><b>재실행 방어를 배치 기록이 아니라 DB 제약에 뒀다.</b> 알림은 코스마다 하나면 되므로 유니크 키
     * {@code (guest_id, type, course_id)} 가 그 자체로 답이다. "오늘 이미 돌았나" 를 따로 기록하면 배치가
     * 반쯤 돌다 죽었을 때 남은 사람들이 영영 못 받는다 — 제약에 얹으면 다시 돌려도 안 만들어진 것만 채운다.
     */
    @Scheduled(cron = DAILY_AT_EVENING, zone = SERVICE_ZONE_ID)
    @Transactional
    public void notifyTripsStartingTomorrow() {
        notifyTripsStartingTomorrow(LocalDate.now(SERVICE_ZONE));
    }

    /**
     * 기준일 다음 날 떠나는 코스에 알림을 만든다.
     *
     * <p>기준일을 인자로 받는 이유는 <b>"내일" 이 언제인지를 호출자가 정할 수 있어야</b> 하기 때문이다.
     * 스케줄러는 오늘을 넘기고, 테스트는 고정된 날짜를 넘긴다.
     *
     * <p>외부 호출이 없어 전체를 한 트랜잭션으로 묶는다. 중간에 실패해 통째로 롤백돼도, 다음 실행이
     * 유니크 제약 덕에 <b>아직 안 만들어진 것만</b> 채운다.
     *
     * <p>스케줄러가 부르는 무인자 메서드에도 {@code @Transactional} 을 둔 것은 self-invocation 때문이다 —
     * 같은 빈 안에서 직접 부르면 프록시를 거치지 않아 이 어노테이션이 무력해진다.
     *
     * @return 이 실행으로 새로 만들어진 알림 수
     */
    @Transactional
    public int notifyTripsStartingTomorrow(LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        List<Course> departing = courseRepository.findByTravelDate(tomorrow);
        if (departing.isEmpty()) {
            log.info("여행 전날 알림 — 대상 없음 travelDate={}", tomorrow);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        int created = 0;
        for (Course course : departing) {
            if (notificationRepository.saveIfAbsent(Notification.builder()
                    .guestId(course.getGuestId())
                    .type(NotificationType.TRIP_TOMORROW)
                    .courseId(course.getId())
                    .createdAt(now)
                    .build())) {
                created++;
            }
        }
        // 조용히 0건인 상태를 아무도 모르면 안 된다. 이미 있어 건너뛴 수까지 함께 남겨,
        // "대상은 있는데 새로 만든 게 없다"(재실행)와 "대상이 없다"를 로그만으로 가른다.
        log.info("여행 전날 알림 생성 travelDate={} 대상={}건 새로 만듦={}건 이미 있음={}건",
                tomorrow, departing.size(), created, departing.size() - created);
        return created;
    }
}
