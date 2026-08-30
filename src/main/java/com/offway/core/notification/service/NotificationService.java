package com.offway.core.notification.service;

import com.offway.core.common.response.Paging;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationException;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.notification.service.dto.MyNotifications;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 조회·읽음(#263).
 *
 * <p><b>여기서 알림을 만들지 않는다.</b> 무엇이 알림을 만드는지는 별도 작업이다(여행 전날 배치). 이 서비스는
 * 이미 쌓인 것을 보여주고 읽음을 기록한다.
 *
 * <p>외부 호출이 없어 트랜잭션이 짧다 — 전부 DB 만 만진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 읽은 시각 기준 시간대. 서비스가 한국 여행을 다루므로 사용자 로캘과 무관하게 KST 다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;
    private final CourseRepository courseRepository;
    private final RegionRepository regionRepository;

    /**
     * 소유자의 알림 한 페이지 + 안읽음 전체 수.
     *
     * <p><b>페이지로 끊는다</b>(api-convention §목록 페이지네이션). 알림은 지우지 않고 쌓이는 데이터라,
     * 상한이 없으면 오래 쓴 사용자의 목록 한 번이 계속 커진다. 기본값·상한은 {@link Paging} 이 소유한다.
     *
     * <p><b>안읽음 수는 따로 센다.</b> 배지가 쓰는 값이라 페이지 안에서 세면 첫 페이지 크기에서 멈춘다.
     *
     * <p><b>지역명을 함께 채운다</b>(#356). 안 주면 앱이 알림마다 코스를 조회해야 하는데, 알림 다섯 건이면
     * 요청 다섯 번이고 지워진 코스는 404 라 그 알림만 지역명이 빈다.
     */
    @Transactional(readOnly = true)
    public MyNotifications myNotifications(UUID userId, Integer page, Integer size) {
        UUID owner = Notification.requireOwner(userId);
        Page<Notification> found = notificationRepository.findByOwner(owner, Paging.of(page, size));
        return MyNotifications.of(
                found, regionNamesOf(found.getContent()), notificationRepository.countUnread(owner));
    }

    /**
     * 이 페이지의 알림들이 가리키는 코스의 지역명 — <b>한 페이지에 조회 두 번</b>이다.
     *
     * <p>알림마다 코스를 읽으면 스무 건짜리 페이지가 조회 스무 번이 된다. 코스를 한 번에 읽고, 거기서 나온
     * 지역도 한 번에 읽는다.
     *
     * <p><b>못 찾은 것은 지도에 넣지 않는다.</b> 코스가 지워진 알림이 그렇다 — 알림은 코스가 사라져도 남고,
     * 응답에서 {@code null} 이 되어 앱이 지역명 없는 문구로 되돌린다.
     */
    private Map<Long, String> regionNamesOf(List<Notification> notifications) {
        List<Long> courseIds = notifications.stream()
                .map(Notification::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        List<Course> courses = courseRepository.findByIds(courseIds);
        Map<Long, String> nameByRegionId = regionRepository
                .findByIds(courses.stream().map(Course::getRegionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Region::getId, Region::shortName));
        Map<Long, String> byCourseId = new HashMap<>();
        for (Course course : courses) {
            String name = nameByRegionId.get(course.getRegionId());
            if (name != null) {
                byCourseId.put(course.getId(), name);
            }
        }
        return byCourseId;
    }

    /**
     * 알림 하나를 읽음으로 바꾸고 <b>남은 안읽음 수를 돌려준다</b>.
     *
     * <p>개수를 함께 주는 이유: 앱은 읽은 직후 배지를 고쳐야 하는데, 안 주면 목록을 다시 부른다. 클라이언트가
     * 떠안을 일을 서버가 미리 끝낸다.
     *
     * <p>없는 알림과 남의 알림은 <b>똑같이 404</b> 다. 403 으로 나누면 id 를 훑어 남의 알림 존재를 확인할 수
     * 있다({@code CourseStorageService.get} 과 같은 규칙).
     *
     * @return 처리 후 남은 안읽음 개수
     */
    @Transactional
    public long markRead(UUID userId, long notificationId) {
        UUID owner = Notification.requireOwner(userId);
        // 조회는 남긴다 — 없는 id 와 남의 id 를 404 로 가르는 자리다. UPDATE 만으로는 "0행" 이
        // "이미 읽음" 인지 "남의 것" 인지 구분되지 않는다.
        notificationRepository
                .findOwned(owner, notificationId)
                .orElseThrow(NotificationException::notificationNotFound);
        // 판정과 기록을 한 문장으로 — 읽고 검사하고 쓰면 동시 요청에서 나중 쪽이 처음 읽은 시각을 덮어쓴다.
        notificationRepository.markRead(owner, notificationId, LocalDateTime.now(SERVICE_ZONE));
        return notificationRepository.countUnread(owner);
    }

    /**
     * 소유자의 안 읽은 알림을 전부 읽음 처리하고 <b>남은 안읽음 수를 돌려준다</b>.
     *
     * <p>개수를 다시 센다. 벌크 갱신 직후라 보통 0 이지만, 같은 순간에 새 알림이 들어왔다면 그것까지 읽었다고
     * 답하게 된다 — 화면의 배지가 조용히 거짓말을 한다.
     *
     * @return 처리 후 남은 안읽음 개수
     */
    @Transactional
    public long markAllRead(UUID userId) {
        UUID owner = Notification.requireOwner(userId);
        int changed = notificationRepository.markAllRead(owner, LocalDateTime.now(SERVICE_ZONE));
        log.info("알림 전체 읽음 처리 changed={}", changed);
        return notificationRepository.countUnread(owner);
    }
}
