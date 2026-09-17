package com.offway.core.liveactivity.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.liveactivity.domain.LiveActivityToken;
import com.offway.core.liveactivity.domain.PushToStartToken;
import com.offway.core.liveactivity.domain.TripCountdown;
import com.offway.core.liveactivity.domain.TripProgress;
import com.offway.core.liveactivity.infrastructure.apns.ApnsResult;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivitySender;
import com.offway.core.liveactivity.repository.LiveActivityTokenRepository;
import com.offway.core.liveactivity.repository.PushToStartTokenRepository;
import com.offway.core.liveactivity.service.dto.LiveActivityStartTarget;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 낮에 잠금화면 카드를 <b>띄운다</b>(#583) — 앱이 안 켜져 있어도.
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>#577 은 <b>이미 떠 있는 카드</b>만 갱신할 수 있었다. 카드를 처음 띄우는 일은 앱만 했고, iOS 는
 * 그 카드를 <b>최대 8시간</b> 뒤 스스로 끝낸다. 둘이 겹쳐 두 가지 빈틈이 있었다.
 *
 * <ul>
 *   <li>출발 5일 전부터 띄우기로 했는데 그 5일 동안 <b>앱을 안 연 사람은 끝내 못 본다</b>
 *   <li>자정 갱신이 실제로 먹는 것은 <b>그날 16시 이후에 앱을 연 경우뿐</b>이다 — 낮에 띄운 카드는
 *       자정 전에 이미 끝나 있다
 * </ul>
 *
 * <h2>왜 정오인가</h2>
 *
 * <p>카드는 8시간 살고 잠금화면에는 12시간 남는다. 정오에 띄우면 <b>20시까지 활성, 24시까지 잔류</b>라
 * 오후와 저녁을 덮는다. 자정에 띄우면 00~08시만 활성이라 <b>점심 전에 사라진다</b> — 사람이 잠금화면을
 * 가장 자주 보는 시간대를 통째로 놓친다.
 *
 * <p>자정 갱신({@link LiveActivityRefresher})은 그대로 둔다. 저녁에 앱을 열어 띄운 카드는 그쪽이
 * 날짜를 맞춰 준다.
 *
 * <h2>이미 떠 있으면 띄우지 않는다</h2>
 *
 * <p>같은 카드에 {@code start} 를 또 보내면 하나가 더 생겨 잠금화면에 둘이 뜬다. 그래서 그 (사용자,
 * 코스)의 갱신 토큰이 있으면 {@code update} 로 간다.
 *
 * <p><b>그런데 갱신 토큰이 남아 있어도 카드는 죽어 있을 수 있다</b>(8시간이 지났다). 그때 APNs 가
 * {@code 410} 을 주므로, <b>같은 회차 안에서</b> 그 행을 지우고 곧바로 {@code start} 로 넘어간다 —
 * 다음 회차로 미루면 그날 카드를 통째로 놓친다.
 */
@Slf4j
@Service
public class LiveActivityStarter {

    /**
     * 띄우는 시각 — 정오.
     *
     * <p>위 클래스 주석의 8시간·12시간이 이 값의 근거다. 바꾸려면 그 계산을 다시 해야 한다.
     */
    private static final String DAILY_AT_NOON = "0 0 12 * * *";

    /** 서비스 기준 시간대. 여행 날짜는 한국 사용자의 달력 기준이라 서버 로케일에 맡기지 않는다. */
    private static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 실행 기록의 배치 이름 — {@code batch_run} 의 키다.
     *
     * <p><b>건너뛰기 판정에 쓰지 않는다.</b> 기록이 없으면 "안 돌았다" 와 "대상이 0건이었다" 를 못
     * 가르는데(#309), 가드로 쓰면 반쯤 돌다 죽은 날 나머지 사람이 하루를 통째로 건너뛴다.
     */
    private static final String BATCH_NAME = "live-activity-start";

    private final PushToStartTokenRepository pushToStartTokenRepository;
    private final LiveActivityTokenRepository liveActivityTokenRepository;
    private final CourseRepository courseRepository;
    private final RegionQuery regionQuery;
    private final LiveActivitySender liveActivitySender;
    private final BatchRunRepository batchRunRepository;

    /**
     * 발송 전용 풀. 요청 처리 스레드를 쓰면 배치가 사용자 요청과 자원을 다툰다.
     *
     * <p>동시 상한은 {@link LiveActivityDispatcher#MAX_CONCURRENT_SENDS} 를 <b>그대로 쓴다</b> —
     * 같은 APNs 를 두드리는 팬아웃인데 경로마다 다르게 두면 어느 쪽이 상대를 밀어내는지 알 수 없다.
     */
    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public LiveActivityStarter(
            PushToStartTokenRepository pushToStartTokenRepository,
            LiveActivityTokenRepository liveActivityTokenRepository,
            CourseRepository courseRepository,
            RegionQuery regionQuery,
            LiveActivitySender liveActivitySender,
            BatchRunRepository batchRunRepository) {
        this.pushToStartTokenRepository = pushToStartTokenRepository;
        this.liveActivityTokenRepository = liveActivityTokenRepository;
        this.courseRepository = courseRepository;
        this.regionQuery = regionQuery;
        this.liveActivitySender = liveActivitySender;
        this.batchRunRepository = batchRunRepository;
    }

    /**
     * 매일 정오, 오늘 여행이 걸린 사람의 잠금화면에 카드를 띄운다.
     *
     * <p><b>시각을 먼저 붙잡는다.</b> {@code markStarted} 의 계약은 <i>시작</i> 시각인데, {@code finally}
     * 에서 현재 시각을 읽으면 끝난 시각이 기록된다(#577 리뷰에서 나온 것과 같은 자리다).
     */
    @Scheduled(cron = DAILY_AT_NOON, zone = SERVICE_ZONE_ID)
    public void startDaily() {
        LocalDateTime startedAt = LocalDateTime.now(SERVICE_ZONE);
        try {
            start(LocalDate.now(SERVICE_ZONE));
        } finally {
            batchRunRepository.markStarted(BATCH_NAME, startedAt);
        }
    }

    /**
     * 기준일로 카드를 띄운다.
     *
     * <p>기준일을 인자로 받는다 — 배치는 오늘을 넘기고 테스트는 고정된 날짜를 넘긴다.
     *
     * @return 실제로 <b>새로 띄운</b> 카드 수. 갱신으로 끝난 것은 세지 않는다
     */
    public int start(LocalDate today) {
        List<LiveActivityStartTarget> targets = targetsFor(today);
        if (targets.isEmpty()) {
            log.info("잠금화면 띄우기 — 대상 없음 today={}", today);
            return 0;
        }
        return send(targets);
    }

    /**
     * 오늘 누구의 어느 여행을 띄울지 정한다.
     *
     * <p><b>사람마다 코스를 따로 묻는다.</b> 모수가 전체 사용자가 아니라 <b>띄우기 토큰을 올린 기기</b>
     * 의 주인이라 그만큼만 돈다. 질의는 {@code user_id} 인덱스를 타고, 하루에 한 번이다.
     */
    private List<LiveActivityStartTarget> targetsFor(LocalDate today) {
        List<PushToStartToken> devices = pushToStartTokenRepository.findAll();
        if (devices.isEmpty()) {
            return List.of();
        }

        // 사람마다 한 번만 고른다 — 같은 사람의 기기가 둘이면 같은 카드를 둘 다에 띄운다.
        Map<UUID, Optional<Course>> picked = new HashMap<>();
        Map<CourseOwner, LiveActivityToken> showing = showingCards();
        Map<Long, String> regionNames = new HashMap<>();

        List<LiveActivityStartTarget> targets = new ArrayList<>();
        int alreadyShowing = 0;
        for (PushToStartToken device : devices) {
            Optional<Course> course = picked.computeIfAbsent(
                    device.getUserId(), userId -> pickFor(userId, today));
            if (course.isEmpty()) {
                continue;
            }
            Course trip = course.get();
            LiveActivityToken update = showing.get(new CourseOwner(device.getUserId(), trip.getId()));
            if (update != null) {
                alreadyShowing++;
            }
            targets.add(LiveActivityStartTarget.builder()
                    .pushToStartRowId(device.getId())
                    .pushToStartToken(device.getToken())
                    .updateRowId(update == null ? null : update.getId())
                    .updateToken(update == null ? null : update.getToken())
                    .courseId(trip.getId())
                    .progress(TripProgress.of(trip.getTravelDate(), trip.getTravelDays(), today))
                    .regionName(regionNameOf(trip, regionNames))
                    .startDate(trip.getTravelDate())
                    .endDate(TripProgress.endDate(trip.getTravelDate(), trip.getTravelDays()))
                    .build());
        }
        log.info("잠금화면 띄우기 대상 today={} 기기={}대 이미떠있음={}대", today, targets.size(), alreadyShowing);
        return targets;
    }

    /**
     * 그 사람이 오늘 볼 여행 <b>하나</b>.
     *
     * <p>고르는 규칙은 {@link TripCountdown} 이 갖는다 — 앱에도 같은 이름의 같은 규칙이 있고, 어긋나면
     * 앱을 연 사람과 안 연 사람이 서로 다른 카드를 본다.
     *
     * <p>{@code findUpcoming} 은 <b>종료일이 오늘 이후</b>인 코스를 출발일 오름차순으로 준다 — 진행
     * 중인 것과 앞으로 떠날 것이 모두 들어 있어, 두 갈래를 한 번의 질의로 덮는다.
     */
    private Optional<Course> pickFor(UUID userId, LocalDate today) {
        List<Course> upcoming = courseRepository.findUpcoming(userId, today);
        Map<Long, Course> byId =
                upcoming.stream().collect(Collectors.toMap(Course::getId, course -> course));
        List<TripCountdown.Trip> trips = upcoming.stream()
                .map(course ->
                        new TripCountdown.Trip(course.getId(), course.getTravelDate(), course.getTravelDays()))
                .toList();
        return TripCountdown.pick(trips, today).map(trip -> byId.get(trip.courseId()));
    }

    /** 지금 떠 있는 카드들 — (주인, 코스)로 찾는다. */
    private Map<CourseOwner, LiveActivityToken> showingCards() {
        Map<CourseOwner, LiveActivityToken> showing = new HashMap<>();
        for (LiveActivityToken token : liveActivityTokenRepository.findAll()) {
            showing.putIfAbsent(new CourseOwner(token.getUserId(), token.getCourseId()), token);
        }
        return showing;
    }

    /**
     * 여행지 이름 — 지역마다 한 번만 찾는다.
     *
     * <p><b>짧은 이름({@code 정선})이 아니라 시군구 이름({@code 정선군})을 쓴다</b> — 앱이
     * {@code "정선군 여행"} 으로 조립하기 때문이다(#577 과 같은 이유).
     *
     * <p><b>여기서 예외가 올라가면 그날 카드가 통째로 안 뜬다.</b> 이름은 곁가지라, 못 찾으면
     * 이름 없이 보내고 왜 그랬는지만 남긴다.
     */
    private String regionNameOf(Course course, Map<Long, String> cache) {
        Long regionId = course.getRegionId();
        if (regionId == null) {
            return null;
        }
        return cache.computeIfAbsent(regionId, id -> {
            try {
                return regionQuery.byId(id).map(Region::getSigungu).orElse(null);
            } catch (RuntimeException e) {
                log.warn("여행지 이름을 못 찾아 이름 없이 띄웁니다 regionId={} cause={}",
                        id, e.getClass().getSimpleName());
                return null;
            }
        });
    }

    /**
     * 보내고, 죽은 토큰을 걷어낸다.
     *
     * <p><b>동시 상한을 둔다.</b> 순차로 돌면 지연이 기기 수만큼 곱해지고, 무제한이면 APNs 쪽 속도
     * 제한을 스스로 부른다.
     */
    private int send(List<LiveActivityStartTarget> targets) {
        Semaphore inFlight = new Semaphore(LiveActivityDispatcher.MAX_CONCURRENT_SENDS);
        List<CompletableFuture<Sent>> sending = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> sendOne(target, inFlight), sendExecutor))
                .toList();

        int started = 0;
        int updated = 0;
        int failed = 0;
        int disabled = 0;
        int removed = 0;
        for (CompletableFuture<Sent> future : sending) {
            Sent sent = future.join();
            switch (sent.result()) {
                case SENT -> {
                    if (sent.started()) {
                        started++;
                    } else {
                        updated++;
                    }
                }
                case GONE -> removed += removeRow(sent.target());
                case DISABLED -> disabled++;
                case FAILED, THROTTLED -> failed++;
            }
            removed += sent.staleUpdateRowsRemoved();
        }

        if (disabled > 0) {
            log.warn("잠금화면 띄우기가 비활성입니다 — APNs 설정이 없습니다 보내지못함={}건", disabled);
        }
        log.info("잠금화면 띄우기 완료 대상={}대 새로띄움={}건 갱신으로처리={}건 실패={}건 정리={}건",
                targets.size(), started, updated, failed, removed);
        return started;
    }

    /**
     * 기기 하나에 보낸다.
     *
     * <p><b>갱신이 {@code 410} 이면 같은 회차에서 띄우기로 넘어간다.</b> 갱신 토큰이 남아 있어도 카드는
     * 8시간 뒤 죽어 있을 수 있는데, 그걸 다음 회차로 미루면 그날 카드를 통째로 놓친다.
     */
    private Sent sendOne(LiveActivityStartTarget target, Semaphore inFlight) {
        inFlight.acquireUninterruptibly();
        try {
            if (target.alreadyShowing()) {
                ApnsResult updated = liveActivitySender.send(target.updateToken(), target.update());
                if (updated != ApnsResult.GONE) {
                    return new Sent(target, updated, false, 0);
                }
                // 카드는 이미 죽었다. 그 행을 지우고 새로 띄운다.
                int cleaned = target.updateRow()
                        .map(liveActivityTokenRepository::deleteById)
                        .orElse(0);
                return new Sent(target, liveActivitySender.send(target.pushToStartToken(), target.start()),
                        true, cleaned);
            }
            return new Sent(target, liveActivitySender.send(target.pushToStartToken(), target.start()), true, 0);
        } finally {
            inFlight.release();
        }
    }

    /**
     * 죽은 띄우기 토큰을 지운다 — 앱을 지웠거나 재설치했다.
     *
     * <p><b>한 건의 삭제 실패로 나머지를 버리지 않는다.</b> 이 정리는 곁가지고, 여기서 예외가 올라가면
     * 이미 성공한 발송의 집계까지 잃는다.
     */
    private int removeRow(LiveActivityStartTarget target) {
        try {
            return pushToStartTokenRepository.deleteById(target.pushToStartRowId());
        } catch (RuntimeException e) {
            log.warn("띄우기 토큰을 지우지 못했습니다 rowId={} cause={}",
                    target.pushToStartRowId(), e.getClass().getSimpleName());
            return 0;
        }
    }

    /** (주인, 코스) — 지금 떠 있는 카드를 찾는 키다. */
    private record CourseOwner(UUID userId, Long courseId) {
    }

    /**
     * 보낸 결과.
     *
     * @param started 띄우기였나(참) 갱신이었나(거짓)
     * @param staleUpdateRowsRemoved 죽은 갱신 토큰을 몇 건 걷어냈나
     */
    private record Sent(
            LiveActivityStartTarget target, ApnsResult result, boolean started, int staleUpdateRowsRemoved) {
    }
}
