package com.offway.core.notification.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.notification.service.dto.PushTarget;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 여행 전날 알림을 <b>만들기만</b> 한다(#269).
 *
 * <p>발송({@link TripTomorrowNotifier})과 별도 빈으로 나눈 이유는 트랜잭션 경계다. 알림 생성은 DB 작업
 * 이라 트랜잭션 안이어야 하고, 푸시 발송은 외부 호출이라 트랜잭션 밖이어야 한다(read-timeout 이 길어
 * 커넥션을 오래 잡는다). 한 빈에 두면 스케줄러가 부르는 메서드에서 트랜잭션 메서드를 직접 호출하게 돼
 * self-invocation 으로 프록시를 우회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripTomorrowNotificationCreator {

    /** 서비스 기준 시간대. 여행 날짜는 한국 사용자의 달력 기준이라 서버 로케일에 맡기지 않는다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CourseRepository courseRepository;
    private final NotificationRepository notificationRepository;

    /**
     * 기준일 다음 날 떠나는 코스에 알림을 만든다.
     *
     * <p>기준일을 인자로 받는 이유는 <b>"내일" 이 언제인지를 호출자가 정할 수 있어야</b> 하기 때문이다.
     * 스케줄러는 오늘을 넘기고, 테스트는 고정된 날짜를 넘긴다.
     *
     * <p><b>주인 없는 코스는 건너뛴다.</b> 담지 않고 링크만 만든 공유 전용 코스는 소유자를 두지 않는 것이
     * 설계라({@code Course.sharedOnly}, #261) 알릴 사람이 없다.
     *
     * <p><b>재실행 방어를 배치 기록이 아니라 DB 제약에 뒀다.</b> 알림은 코스마다 하나면 되므로 유니크 키
     * {@code (user_id, type, course_id)} 가 그 자체로 답이다. "오늘 이미 돌았나" 를 따로 기록하면 배치가
     * 반쯤 돌다 죽었을 때 남은 사람들이 영영 못 받는다 — 제약에 얹으면 다시 돌려도 안 만들어진 것만 채운다.
     *
     * <p><b>전체를 한 트랜잭션으로 묶지 않는다.</b> 알림은 코스마다 독립이라 한 건의 실패가 나머지를 물 이유가
     * 없다. 묶어 두면 어느 한 건이 터질 때 그날 대상 <b>전원</b>이 알림을 못 받는데, 이 배치는 하루에 한 번이라
     * 다음 실행은 여행이 이미 시작된 뒤다 — "다음 실행이 채운다" 가 여기서는 위로가 되지 않는다.
     *
     * <p>저장은 {@code saveIfAbsent} 가 건마다 자기 트랜잭션으로 처리하고, 여기서는 실패한 건만 세고 넘어간다.
     *
     * @return <b>이 실행으로 새로 만들어진</b> 알림들. 이미 있던 것은 빠진다 — 재실행이 같은 푸시를 다시
     *     보내면 안 되기 때문이다
     */
    public List<PushTarget> createForTripsStartingTomorrow(LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        List<Course> departing = courseRepository.findByTravelDate(tomorrow);
        if (departing.isEmpty()) {
            log.info("여행 전날 알림 — 대상 없음 travelDate={}", tomorrow);
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        List<PushTarget> created = new ArrayList<>();
        int failed = 0;
        int ownerless = 0;
        for (Course course : departing) {
            UUID owner = course.getUserId();
            if (owner == null) {
                // 담지 않고 링크만 만든 공유 전용 코스다(#261) — 주인이 없으니 알릴 사람도 없다.
                // 건너뛰는 것이 정상이라 실패로 세지 않되, 몇 건이 그랬는지는 남긴다.
                ownerless++;
                continue;
            }
            try {
                if (notificationRepository.saveIfAbsent(Notification.builder()
                        .userId(owner)
                        .type(NotificationType.TRIP_TOMORROW)
                        .courseId(course.getId())
                        .createdAt(now)
                        .build())) {
                    created.add(new PushTarget(owner, NotificationType.TRIP_TOMORROW, course.getId()));
                }
            } catch (DataIntegrityViolationException e) {
                // 조회와 삽입 사이에 다른 실행이 같은 것을 넣었다. 유니크 키가 막아 준 것이고 결과는
                // "이미 있음" 과 같으므로 실패로 세지 않는다.
                log.debug("여행 전날 알림이 이미 있어 건너뜁니다 courseId={}", course.getId());
            } catch (RuntimeException e) {
                // 한 건의 실패로 나머지를 버리지 않는다. 다만 조용히 넘기지도 않는다.
                failed++;
                log.warn("여행 전날 알림을 만들지 못했습니다 courseId={} cause={}",
                        course.getId(), e.getClass().getSimpleName());
            }
        }
        // 조용히 0건인 상태를 아무도 모르면 안 된다. 이미 있어 건너뛴 수까지 함께 남겨,
        // "대상은 있는데 새로 만든 게 없다"(재실행)와 "대상이 없다"를 로그만으로 가른다.
        // 주인 없는 코스는 따로 센다 — 실패가 아니라 알릴 사람이 없는 것이라 대응이 다르다.
        log.info("여행 전날 알림 생성 travelDate={} 대상={}건 새로 만듦={}건 이미 있음={}건 주인없음={}건 실패={}건",
                tomorrow, departing.size(), created.size(),
                departing.size() - created.size() - failed - ownerless, ownerless, failed);
        return created;
    }
}
