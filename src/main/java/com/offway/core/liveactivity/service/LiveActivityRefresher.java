package com.offway.core.liveactivity.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.liveactivity.domain.LiveActivityToken;
import com.offway.core.liveactivity.domain.TripProgress;
import com.offway.core.liveactivity.repository.LiveActivityTokenRepository;
import com.offway.core.liveactivity.service.dto.LiveActivityTarget;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 자정에 잠금화면의 여행 D-day 를 하루치 옮긴다(#575).
 *
 * <h2>왜 서버가 해야 하나</h2>
 *
 * <p>앱은 <b>켜져 있을 때만</b> 잠금화면 카드를 고칠 수 있다. 자정을 넘겨 {@code D-3} 이 {@code D-2} 가
 * 돼야 하는데, 다음에 앱을 열기 전까지 어제 숫자가 그대로 남는다. 하루에 한 번 저절로 바뀌어야 제 값을
 * 하는 기능이라 서버 푸시가 필요하다.
 *
 * <h2>대상은 "토큰이 있는 것" 이다</h2>
 *
 * <p>이슈는 "출발 5일 이내" 로 대상을 잡자고 했는데, 여기서는 그 조건을 두지 않는다. <b>등록된 토큰이
 * 있다는 것 자체가 앱이 그 카드를 띄웠다는 뜻</b>이라, 두 조건은 실제로 같은 집합을 가리킨다. 그런데
 * 둘이 어긋나는 순간(앱이 창을 넓히거나, 사용자가 날짜를 뒤로 미루거나) <b>토큰은 있는데 우리가 건너뛰는</b>
 * 카드가 생기고, 그 카드는 영영 옛날 값을 들고 있는다. 날짜로 한 번 더 거르면 막을 수 있는 것이 없고
 * 잃을 것만 있다.
 *
 * <h2>두 단계를 나눠 부른다</h2>
 *
 * <p>대상 선정은 DB, 발송은 외부 호출이다. 한 메서드에 두면 발송하는 동안 DB 커넥션을 잡고 있게 된다.
 *
 * <h2>{@code ManualBatch} 에 넣지 않는다</h2>
 *
 * <p>그 인터페이스가 <b>"사용자에게 나가는 알림 배치는 넣지 않는다"</b> 고 못 박아 뒀다(#537). 손으로
 * 누르면 진짜 사용자의 잠금화면이 바뀐다 — 되돌릴 수 없는 것을 버튼 하나 뒤에 두지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveActivityRefresher {

    /**
     * 갱신 시각 — 자정.
     *
     * <p><b>이 시각이 곧 이 기능이다.</b> 날짜가 바뀌는 순간 값도 바뀌어야 하고, 잠금화면은 사용자가
     * 자는 동안 조용히 갱신되어도 깨우지 않는다(우선순위 5로 보낸다).
     */
    private static final String DAILY_AT_MIDNIGHT = "0 0 0 * * *";

    /** 서비스 기준 시간대. 여행 날짜는 한국 사용자의 달력 기준이라 서버 로케일에 맡기지 않는다. */
    private static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 실행 기록의 배치 이름 — {@code batch_run} 의 키다.
     *
     * <p><b>건너뛰기 판정에 쓰지 않는다.</b> 기록이 없으면 "안 돌았다" 와 "대상이 0건이었다" 를 못 가르는데
     * (#309 에서 실제로 그랬다), 가드로 쓰면 반쯤 돌다 죽은 날 나머지 카드가 하루를 통째로 건너뛴다.
     */
    private static final String BATCH_NAME = "live-activity-refresh";

    private final LiveActivityTokenRepository liveActivityTokenRepository;
    private final CourseRepository courseRepository;
    private final RegionQuery regionQuery;
    private final LiveActivityDispatcher dispatcher;
    private final BatchRunRepository batchRunRepository;

    /** 매일 자정, 잠금화면에 떠 있는 카드를 오늘 값으로 고친다. */
    @Scheduled(cron = DAILY_AT_MIDNIGHT, zone = SERVICE_ZONE_ID)
    public void refreshDaily() {
        try {
            refresh(LocalDate.now(SERVICE_ZONE));
        } finally {
            batchRunRepository.markStarted(BATCH_NAME, LocalDateTime.now(SERVICE_ZONE));
        }
    }

    /**
     * 기준일로 카드를 갱신한다.
     *
     * <p>기준일을 인자로 받는 이유는 <b>"오늘" 이 언제인지를 호출자가 정할 수 있어야</b> 하기 때문이다.
     * 스케줄러는 오늘을 넘기고, 테스트는 고정된 날짜를 넘긴다 — 안에서 현재 시각을 읽으면 이 계산을
     * 검증할 방법이 없어지고, 테스트가 날짜가 바뀌는 날 깨진다.
     *
     * @return 실제로 보낸 건수
     */
    public int refresh(LocalDate today) {
        return dispatcher.dispatch(targetsFor(today));
    }

    /**
     * 오늘 무엇을 보낼지 정한다.
     *
     * <p><b>코스를 못 찾은 등록도 대상이다.</b> 사용자가 코스를 지웠거나 탈퇴 정리가 반쯤 돈 경우인데,
     * 건너뛰면 없는 여행의 카드가 잠금화면에 남는다. 그때는 종료를 보내고 행을 지운다.
     */
    private List<LiveActivityTarget> targetsFor(LocalDate today) {
        List<LiveActivityToken> tokens = liveActivityTokenRepository.findAll();
        if (tokens.isEmpty()) {
            return List.of();
        }

        Map<Long, Course> courses = coursesOf(tokens);
        Map<Long, String> regionNames = regionNamesOf(courses.values());

        List<LiveActivityTarget> targets = new ArrayList<>();
        int ending = 0;
        for (LiveActivityToken token : tokens) {
            Course course = courses.get(token.getCourseId());
            TripProgress progress = progressOf(course, today);
            if (progress.ended()) {
                ending++;
            }
            targets.add(LiveActivityTarget.builder()
                    .rowId(token.getId())
                    .token(token.getToken())
                    .courseId(token.getCourseId())
                    .progress(progress)
                    .regionName(course == null ? null : regionNames.get(course.getRegionId()))
                    .startDate(course == null ? null : course.getTravelDate())
                    .endDate(endDateOf(course))
                    .build());
        }
        log.info("잠금화면 갱신 대상 today={} 카드={}건 종료={}건", today, targets.size(), ending);
        return targets;
    }

    /** 코스가 없으면 끝난 것으로 본다 — 언제인지 모르는 여행을 잠금화면에 띄워 둘 수 없다. */
    private static TripProgress progressOf(Course course, LocalDate today) {
        if (course == null) {
            return TripProgress.of(null, 0, today);
        }
        return TripProgress.of(course.getTravelDate(), course.getTravelDays(), today);
    }

    private static LocalDate endDateOf(Course course) {
        if (course == null || course.getTravelDate() == null) {
            return null;
        }
        return TripProgress.endDate(course.getTravelDate(), course.getTravelDays());
    }

    /**
     * 등록된 코스를 <b>한 번에</b> 가져온다 — 카드마다 조회하면 대상 수만큼 질의가 돈다.
     *
     * <p>같은 코스를 여러 기기에서 띄웠으면 id 가 겹치므로 먼저 중복을 없앤다.
     */
    private Map<Long, Course> coursesOf(List<LiveActivityToken> tokens) {
        List<Long> courseIds =
                tokens.stream().map(LiveActivityToken::getCourseId).distinct().toList();
        return courseRepository.findByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
    }

    /**
     * 여행지 이름을 <b>배치마다 한 번만</b> 모은다 — 값이 89개 지역 중 하나로 겹친다.
     *
     * <p><b>짧은 이름({@code 정선})이 아니라 시군구 이름({@code 정선군})을 쓴다.</b> 알림 배너는 짧은
     * 쪽을 쓰는데(#356), 거기서는 알림함 목록과 같은 말로 부르는 것이 기준이었다. 잠금화면은 앱이
     * {@code "정선군 여행"} 으로 조립하고 있어 기준이 다르다 — 두 화면이 같아야 하는 대상이 서로 다르다.
     *
     * <p><b>여기서 예외가 올라가면 그날 갱신이 통째로 안 나간다.</b> 이름은 곁가지라, 조회가 실패하면
     * 전부 이름 없이 내려보내고 왜 그랬는지만 남긴다.
     */
    private Map<Long, String> regionNamesOf(java.util.Collection<Course> courses) {
        List<Long> regionIds =
                courses.stream().map(Course::getRegionId).distinct().toList();
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        try {
            return regionQuery.byIds(regionIds).stream()
                    .collect(Collectors.toMap(Region::getId, Region::getSigungu));
        } catch (RuntimeException e) {
            log.warn("여행지 이름을 못 찾아 이름 없이 보냅니다 지역={}곳 cause={}",
                    regionIds.size(), e.getClass().getSimpleName());
            return Map.of();
        }
    }
}
