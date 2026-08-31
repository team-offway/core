package com.offway.core.notification.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.notification.service.dto.PushTarget;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 코스 목록에 알림을 만든다 — <b>배치마다 다른 것은 대상과 종류뿐</b>(#302).
 *
 * <p>여행 전날(#269)과 여행 종료 다음 날(#302)이 같은 일을 한다. 코스를 돌며 알림을 만들고, 이미 있으면
 * 건너뛰고, 한 건이 실패해도 나머지를 이어가고, 결과를 센다. 다른 것은 <b>어떤 코스를 고르느냐</b>와
 * <b>어떤 종류를 다느냐</b> 뿐이라 그 둘만 인자로 받는다.
 *
 * <p><b>발송과 별도 빈인 이유는 트랜잭션 경계다.</b> 알림 생성은 DB 작업이라 트랜잭션 안이어야 하고, 푸시
 * 발송은 외부 호출이라 트랜잭션 밖이어야 한다(read-timeout 이 길어 커넥션을 오래 잡는다). 한 빈에 두면
 * 스케줄러가 부르는 메서드에서 트랜잭션 메서드를 직접 호출하게 돼 self-invocation 으로 프록시를 우회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseNotificationWriter {

    /** 서비스 기준 시간대. 여행 날짜는 한국 사용자의 달력 기준이라 서버 로케일에 맡기지 않는다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;

    /**
     * 코스마다 알림 하나를 만든다.
     *
     * <p><b>재실행 방어를 배치 기록이 아니라 DB 제약에 뒀다.</b> 알림은 코스마다 하나면 되므로 유니크 키
     * {@code (소유자, type, course_id)} 가 그 자체로 답이다. "오늘 이미 돌았나" 를 따로 기록하면 배치가
     * 반쯤 돌다 죽었을 때 남은 사람들이 영영 못 받는다 — 제약에 얹으면 다시 돌려도 안 만들어진 것만 채운다.
     *
     * <p><b>전체를 한 트랜잭션으로 묶지 않는다.</b> 알림은 코스마다 독립이라 한 건의 실패가 나머지를 물 이유가
     * 없다. 묶어 두면 어느 한 건이 터질 때 그날 대상 <b>전원</b>이 알림을 못 받는데, 이 배치들은 하루에 한 번이라
     * 다음 실행은 이미 늦다. 저장은 {@code saveIfAbsent} 가 건마다 자기 트랜잭션으로 처리하고, 여기서는
     * 실패한 건만 세고 넘어간다.
     *
     * @param label 로그에 남길 배치 이름 — 두 배치가 같은 로그 문장을 쓰므로 어느 쪽인지 구분되어야 한다
     * @return <b>이 실행으로 새로 만들어진</b> 알림들. 이미 있던 것은 빠진다 — 재실행이 같은 푸시를 다시
     *     보내면 안 되기 때문이다
     */
    public List<PushTarget> create(List<Course> courses, NotificationType type, String label) {
        if (courses.isEmpty()) {
            log.info("{} — 대상 없음", label);
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        List<PushTarget> created = new ArrayList<>();
        int failed = 0;
        for (Course course : courses) {
            try {
                // 만들어진 id 를 그대로 푸시에 싣는다(#357) — 배너를 눌러 들어온 앱이 그 자리에서
                // 읽음 처리하려면 어느 알림인지가 필요하다.
                notificationRepository.saveIfAbsent(Notification.builder()
                                .userId(course.getUserId())
                                .type(type)
                                .courseId(course.getId())
                                .createdAt(now)
                                .build())
                        .ifPresent(notificationId -> created.add(PushTarget.builder()
                                .userId(course.getUserId())
                                .type(type)
                                .courseId(course.getId())
                                .notificationId(notificationId)
                                .build()));
            } catch (DataIntegrityViolationException e) {
                // 조회와 삽입 사이에 다른 실행이 같은 것을 넣었다. 유니크 키가 막아 준 것이고 결과는
                // "이미 있음" 과 같으므로 실패로 세지 않는다.
                log.debug("{} 알림이 이미 있어 건너뜁니다 courseId={}", label, course.getId());
            } catch (RuntimeException e) {
                // 한 건의 실패로 나머지를 버리지 않는다. 다만 조용히 넘기지도 않는다.
                failed++;
                log.warn("{} 알림을 만들지 못했습니다 courseId={} cause={}",
                        label, course.getId(), e.getClass().getSimpleName());
            }
        }
        // 조용히 0건인 상태를 아무도 모르면 안 된다. 이미 있어 건너뛴 수까지 함께 남겨,
        // "대상은 있는데 새로 만든 게 없다"(재실행)와 "대상이 없다"를 로그만으로 가른다.
        log.info("{} 생성 대상={}건 새로 만듦={}건 이미 있음={}건 실패={}건",
                label, courses.size(), created.size(), courses.size() - created.size() - failed, failed);
        return created;
    }
}
