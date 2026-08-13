package com.offway.core.itinerary.service;

import com.offway.core.common.response.Paging;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseScope;
import com.offway.core.itinerary.domain.DayStart;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse.FirstDayChange;
import com.offway.core.itinerary.service.dto.MyCourses;
import com.offway.core.leave.service.MyLeaveService;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.service.dto.TrainAccess;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.TrainAccessService;
import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 저장·조회(#33) — 생성된 코스를 게스트의 "내 코스"로 영속화하고 다시 꺼낸다. 혜택은 저장하지 않고 조회 시점에 정책 매칭으로
 * 다시 붙인다(저장 코스가 정책 변경에 뒤처지지 않게). 애그리거트 저장이라 외부 호출 없이 짧은 트랜잭션.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseStorageService {

    /** D-day·다가오는 여행 판정 기준 시간대. 서비스가 한국 여행을 다루므로 사용자 로캘과 무관하게 KST 다. */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 공유 링크 발급이 예상 밖 제약에 걸렸을 때 — DB 메시지를 싣지 않는다(토큰이 들어 있다). */
    private static final String SHARE_ISSUE_FAILED = "공유 링크를 발급하지 못했습니다 courseId=%d";

    private final CourseRepository courseRepository;
    private final PolicyService policyService;
    private final RegionRepository regionRepository;
    private final CourseWeatherProvider courseWeatherProvider;
    private final OpeningHoursProvider openingHoursProvider;
    private final CoursePersistenceService coursePersistenceService;
    private final CourseLeaveDeductionService courseLeaveDeductionService;
    private final MyLeaveService myLeaveService;
    private final TrainAccessService trainAccessService;

    /** 이미 조립된 게스트 코스를 저장하고, 혜택을 붙여 돌려준다. 구성 검증·계약 예외 번역은 입력 경계(요청 DTO)가 소유한다. */
    public GeneratedCourse save(Course course) {
        // 저장만 트랜잭션 안에서 한다(별도 빈이라 프록시를 탄다) — 조회 경로(get)와 같은 모양이다.
        //
        // 예전에는 이 메서드가 통째로 @Transactional 이었고, "트랜잭션 안이라 외부를 안 붙인다" 는 이유로
        // 열차 접근만 껐다. 그런데 같은 조립 안에 날씨가 있어, 실제로는 커넥션을 잡은 채 기상청을 Day 수만큼
        // 기다리고 있었다(실측 1.15초 × 3). 경계를 옮겨 조립 전체를 트랜잭션 밖으로 뺀다.
        //
        // 열차 접근은 여전히 붙이지 않는다 — 트랜잭션과 무관하게, 클라이언트가 방금 생성 응답에서 받은
        // 값을 갖고 있어 다시 줄 이유가 없다(#187).
        Course saved = coursePersistenceService.persist(course);
        // 공유 토큰을 저장 응답에 함께 싣는다(#143) — 공유 버튼이 추가 왕복 없이 링크를 만들게.
        // 토큰이 있다고 공개되는 것이 아니다. 링크를 넘겨야 비로소 남이 볼 수 있다.
        return withBenefits(saved, false).withShareToken(shareTokenOf(saved.getId()));
    }

    /**
     * 코스의 공유 토큰 — 동시에 발급하려는 경합을 흡수한다.
     *
     * <p>{@code findByCourseId} 후 {@code save} 사이에 다른 요청이 끼어들면 둘 다 "없다" 를 읽고 하나가
     * 유니크 제약({@code uk_course_share_course})에 걸린다. 그대로 두면 500 이 나가는데, 사용자가 원한 상태
     * (링크가 하나 있다)는 이미 이뤄져 있다.
     *
     * <p><b>중복인지 확인하고 삼킨다.</b> 확인 없이 삼키면 다른 제약 위반까지 조용히 넘어가, 토큰 없이
     * 200 을 주고도 아무도 모른다 — 규약이 막는 '조용한 실패' 다.
     *
     * <p>지금 이 경합은 사실상 닿지 않는다(방금 만든 코스라 그 id 를 아는 요청이 하나뿐이다). 다만 발급을
     * 별도 엔드포인트로 여는 순간 바로 도달 가능해지고, 이 메서드의 계약("코스당 링크 하나")은 그때도 같다.
     */
    private String shareTokenOf(Long courseId) {
        try {
            return coursePersistenceService.shareOf(courseId).getShareToken();
        } catch (DataIntegrityViolationException e) {
            return coursePersistenceService
                    .findShare(courseId)
                    .map(share -> {
                        log.info("공유 링크 발급 경합 — 먼저 만들어진 링크를 그대로 씁니다 courseId={}", courseId);
                        return share.getShareToken();
                    })
                    .orElseThrow(() -> {
                        // **예외 객체를 그대로 찍지 않는다.** MySQL 의 중복 키 메시지는
                        // `Duplicate entry 'xyz' for key 'uk_course_share_token'` 형태라 토큰이 그대로 실린다.
                        // 그 토큰이 곧 공개 링크의 자격증명이라, 로그를 보는 사람이 남의 코스를 열 수 있게 된다
                        // (로깅 규약: 토큰을 로그에 남기지 않는다).
                        //
                        // 하필 여기가 그 자리다 — course_id 중복은 위에서 처리됐으므로, 여기까지 왔다는 것은
                        // 남은 제약(토큰 유니크)이 걸렸다는 뜻이고 그 메시지에 토큰이 들어 있다.
                        log.error("공유 링크 발급 실패 — 중복이 아닌 제약 위반 courseId={} cause={}",
                                courseId, e.getClass().getSimpleName());
                        // **원본을 다시 던지지 않는다.** 던지면 전역 핸들러가 스택째 찍어 같은 값이 새어 나가,
                        // 여기서 가린 것이 무의미해진다. 사유는 위 로그와 courseId 로 추적된다.
                        return new IllegalStateException(SHARE_ISSUE_FAILED.formatted(courseId));
                    });
        }
    }

    /**
     * 공유 링크로 여는 코스(#143) — <b>인증 없이, 소유자 확인 없이</b>.
     *
     * <p>접근을 막는 것은 소유자가 아니라 추측 불가능한 토큰이다. 그래서 이 경로는 소유자 식별자를 절대
     * 반환하지 않는다 — 응답 조립은 {@link CourseResponse#publicView} 가 내부 식별자를 걷어낸다.
     *
     * <p><b>공유 토큰을 얹지 않는다</b> — 소유자 경로({@link #get})가 #259 에서 토큰을 싣게 됐지만 여기는
     * 그대로다. 링크를 받은 사람은 이 코스를 수정·삭제할 수 없고, 토큰은 이미 자기가 연 URL 에 있다.
     */
    public GeneratedCourse findShared(String shareToken) {
        Course course = coursePersistenceService.loadShared(shareToken);
        return withBenefits(course, true);
    }

    /**
     * 게스트의 저장 코스 <b>한 페이지</b> — 보는 범위({@link CourseScope})에 따라 다가오는 여행 · 지난 여행 · 전부.
     *
     * <p>어느 코스를 연차 차감했는지 함께 준다. 화면이 "확정함" 을 표시하려면 필요한데, <b>코스마다 물으면 코스 수만큼
     * 쿼리가 늘어난다</b> — 한 번에 모아 온다.
     *
     * <p><b>페이지로 끊는 이유</b>(#105). 코스 하나가 애그리거트({@code DaySchedule}→{@code Slot})를 통째로
     * 끌고 오므로, 코스가 쌓이면 목록 요청 한 번이 수백~수천 행을 힙에 올린다. 기본값·상한은 {@link Paging} 이 소유한다.
     */
    @Transactional(readOnly = true)
    public MyCourses myCourses(String guestId, CourseScope scope, Integer page, Integer size) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        Page<Course> found = scope.find(courseRepository, guestId, today, Paging.of(page, size));
        List<Course> courses = found.getContent();
        courses.forEach(Course::totalSlots); // 응답 직렬화는 tx 밖 — 애그리거트(days·slots)를 여기서 초기화
        return MyCourses.from(
                found,
                myLeaveService.deductedCourseIds(guestId),
                regionNamesOf(courses),
                shareTokensOf(courses),
                today);
    }

    /**
     * 범위 안의 코스 <b>전부</b> — 서버 안에서 더 걸러 쓰는 호출자용(지난 여행 확인 등).
     *
     * <p>응답으로 그대로 나가는 길이 아니다. 화면에 내리는 목록은 {@link #myCourses} 로 페이지를 끊는다.
     */
    @Transactional(readOnly = true)
    public MyCourses allCourses(String guestId, CourseScope scope) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        List<Course> courses = scope.find(courseRepository, guestId, today);
        courses.forEach(Course::totalSlots);
        return MyCourses.all(courses, myLeaveService.deductedCourseIds(guestId), regionNamesOf(courses), today);
    }

    /**
     * 게스트 소유의 저장 코스 상세(혜택 포함). 소유자 범위로만 조회해 남의 코스를 ID 만으로 볼 수 없게 한다. 없거나 소유자가
     * 아니면 존재 여부를 흘리지 않도록 똑같이 404.
     */
    public GeneratedCourse get(String guestId, long courseId) {
        // 로딩만 트랜잭션 안에서 한다(별도 빈이라 프록시를 탄다). 날씨는 외부 호출이라 밖에서 붙인다(#169).
        Course course = coursePersistenceService.loadOwned(guestId, courseId);
        // 공유 토큰을 상세에도 싣는다(#259). 저장 응답에만 실어 두니, 그 응답을 놓친 코스는 영영 공유할 수
        // 없었다 — 앱을 다시 깔거나 기기를 바꾸면 이전 코스가 전부 그랬다.
        //
        // **여기서 발급까지 한다.** 없는 것을 null 로 두면 #143 이전에 저장된 코스가 그대로 공유 불가로 남는다.
        // 조회가 쓰기를 하는 셈이지만, 코스당 한 번뿐이고 두 번째부터는 있는 것을 읽어 준다.
        return withBenefits(course, true).withShareToken(shareTokenOf(course.getId()));
    }

    /**
     * 저장 코스의 여행 날짜를 고친다(#170) — 편집 시트의 "여행날짜 수정".
     *
     * <p><b>연차 차감량을 함께 다시 계산한다.</b> 날짜가 바뀌면 구간의 평일 수·공휴일이 달라져 깎을 연차가
     * 달라진다. 그대로 두면 화면의 새 날짜와 깎인 연차가 서로 다른 여행을 가리키게 된다.
     *
     * <p><b>단계를 나눈 이유는 트랜잭션 경계다.</b> 재계산은 공휴일 조회(특일정보)를 타는 외부 호출이라
     * 트랜잭션 안에 넣으면 read-timeout 동안 DB 커넥션을 잡는다(영속성 규약). 계산을 밖에서 끝내고,
     * 날짜 갱신과 차감 갱신만 {@link CoursePersistenceService} 의 한 트랜잭션으로 묶는다.
     *
     * <p>지난 날짜 거절을 <b>계산 전에</b> 한 번 더 한다. 어차피 400 으로 돌려보낼 요청 때문에 외부 API 를
     * 부를 이유가 없다. 규칙 자체는 {@link Course#requireChangeableTo} 하나뿐이라 두 자리가 갈리지 않는다.
     */
    public GeneratedCourse changeTravelDate(String guestId, long courseId, LocalDate travelDate) {
        Course.requireChangeableTo(travelDate, LocalDate.now(SERVICE_ZONE));
        Course course = coursePersistenceService.loadOwned(guestId, courseId);
        OptionalDouble recalculated = courseLeaveDeductionService.recalculateFor(course, travelDate);
        Double deductionDays = recalculated.isPresent() ? recalculated.getAsDouble() : null;
        Course updated = coursePersistenceService.applyTravelDate(guestId, courseId, travelDate, deductionDays);

        // 새 날짜의 도착 시각으로 첫날을 다시 판정한다(#214). 열차는 이미 새 날짜로 조회되는데 그 시각으로
        // 내린 일정 판단은 저장된 옛것이라, 그대로 두면 도착 전 시간에 일정이 잡힌 코스가 남는다.
        Region region = regionOf(updated);
        TrainAccess trainAccess = trainAccessFor(updated, region);
        FirstDayChange change = realignFirstDay(guestId, courseId, updated, travelDate, trainAccess);
        Course finalCourse = change == FirstDayChange.TRIMMED
                ? coursePersistenceService.loadOwned(guestId, courseId) // 걷어낸 결과로 다시 읽는다
                : updated;

        // 상세 조회와 같은 모양으로 돌려준다 — 화면이 날짜·날씨·요일을 새 날짜 기준으로 다시 그려야 한다.
        // 공유 토큰도 같은 이유로 함께 싣는다(#259). 여기서만 빠지면 날짜를 고친 순간 화면의 공유 버튼이
        // 사라진다.
        return assemble(finalCourse, region, trainAccess)
                .withShareToken(shareTokenOf(courseId))
                .withFirstDayChange(change);
    }

    /**
     * 새 날짜의 도착 시각으로 첫날을 맞춘다(#214) — <b>걷어내기만</b> 한다.
     *
     * <p>도착이 늦어져 갈 수 없게 된 슬롯은 서버가 지운다. 반대로 도착이 빨라져 첫날을 쓸 수 있게 됐다면
     * 채울 후보가 저장 코스에 없어(슬롯만 남아 있다) 서버가 못 고친다 — 알리기만 하고 재생성은 사용자가 고른다.
     *
     * @return 뒤집힘이 없었으면 null
     */
    private FirstDayChange realignFirstDay(
            String guestId, long courseId, Course course, LocalDate travelDate, TrainAccess trainAccess) {
        if (trainAccess == null) {
            return null; // 자차·출발지 없음 — 애초에 첫날 판단이 없다
        }
        DayStart start = trainAccess.arrivalAt()
                .map(arriveAt -> DayStart.afterArriving(travelDate, arriveAt))
                .orElseGet(DayStart::fullDay);

        if (course.firstDayEmptyOnCalendar()) {
            // 첫날이 비어 있는데 이제 쓸 수 있다 — 하루를 통째로 버리고 있다는 뜻이다.
            return start.equals(DayStart.none()) ? null : FirstDayChange.FILLABLE;
        }
        int removed = coursePersistenceService.realignFirstDay(guestId, courseId, start);
        if (removed == 0) {
            return null;
        }
        log.info("날짜 수정으로 첫날 슬롯을 걷어냈습니다 courseId={} removed={}", courseId, removed);
        return FirstDayChange.TRIMMED;
    }

    /**
     * 저장 코스의 열차 접근을 <b>다시 계산</b>한다(#187) — 생성 때만 실리고 저장하면 사라지던 값이다.
     *
     * <p><b>결과가 아니라 입력(출발지)을 저장해 두고 여기서 계산한다.</b> 열차 시간표는 바뀌므로 생성 시점의
     * 시간을 그대로 보관하면 한 달 뒤 여행에서 낡은 시간을 보여주게 되고, 그건 없는 것보다 나쁘다.
     *
     * <p><b>상세 조회에서만 부른다.</b> 목록({@link #myCourses})에서 하면 코스 수만큼 외부 호출이 붙고,
     * 저장 응답은 트랜잭션 안이라 외부를 부르면 커넥션을 오래 잡는다(영속성 규약).
     *
     * @return 열차 접근. 자차 코스·출발지 없음·지역 좌표 없음이면 null(오류가 아니다)
     */
    private TrainAccess trainAccessFor(Course course, Region region) {
        if (course.getTransport() != TransportMode.TRANSIT || region == null) {
            return null;
        }
        // 이 필드가 생기기 전에 저장된 코스는 근거가 없다. 지어내지 않는다.
        return course.origin()
                .map(origin -> trainAccessService.accessTo(
                        origin.lat(), origin.lng(), region.getLat(), region.getLng(), course.getTravelDate()))
                .orElse(null);
    }

    /**
     * 게스트 소유의 저장 코스를 지운다.
     *
     * <p>조회와 <b>같은 규칙</b>이다 — 없거나 남의 코스면 똑같이 404 다. 403 으로 나누면 "그 ID 는 존재하는데 네 것이
     * 아니다" 를 알려주는 셈이라, ID 를 훑어 남의 코스 존재를 확인할 수 있다.
     *
     * <p>hard delete 다. 하위(DaySchedule·Slot)는 애그리거트 내부라 {@code cascade = ALL} ·
     * {@code orphanRemoval} 로 함께 지워진다.
     *
     * <p><b>이 메서드에 트랜잭션을 걸지 않는다.</b> 동시 삭제 충돌은 flush·commit 시점에 드러나므로 같은 트랜잭션
     * 안에서는 잡을 수 없다 — {@link CoursePersistenceService} 가 커밋까지 끝낸 뒤 결과를 여기서 받는다.
     */
    public void delete(String guestId, long courseId) {
        try {
            coursePersistenceService.deleteOwned(guestId, courseId);
        } catch (OptimisticLockingFailureException e) {
            // 남이 먼저 지웠다. "없다" 가 정확한 답이고, 순차 재삭제(두 번째 요청)와 같은 계약이 된다.
            throw ItineraryException.courseNotFound();
        }
    }

    /**
     * 코스들이 속한 지역명 — <b>한 번에 모아 조회한다</b>(#171).
     *
     * <p>코스마다 지역을 따로 읽으면 FE 의 N+1 을 서버로 옮긴 것뿐이다. 지역 ID 를 중복 없이 모아 한 번 부른다.
     */
    private Map<Long, String> regionNamesOf(List<Course> courses) {
        List<Long> regionIds = courses.stream().map(Course::getRegionId).distinct().toList();
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        return regionRepository.findByIds(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Region::getSigungu, (a, b) -> a));
    }

    /**
     * 목록에 실을 공유 토큰 — <b>한 번에 모아 조회한다</b>(#259).
     *
     * <p>코스마다 물으면 한 페이지에 쿼리가 코스 수만큼 늘어난다(N+1). 목록은 페이지당 최대 100건이다.
     *
     * <p><b>없는 것을 발급하지는 않는다.</b> 목록 한 번이 최대 100번의 INSERT 가 되기 때문이다. 그런 코스는
     * 상세({@link #get})를 열 때 그 자리에서 발급된다.
     */
    private Map<Long, String> shareTokensOf(List<Course> courses) {
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return coursePersistenceService.shareTokensOf(courseIds);
    }

    private GeneratedCourse withBenefits(Course course, boolean withTrainAccess) {
        Region region = regionOf(course);
        return assemble(course, region, withTrainAccess ? trainAccessFor(course, region) : null);
    }

    /** 코스 지역 — 슬롯 표시명·날씨·열차 접근이 모두 이 값을 쓴다. 한 번만 읽는다. */
    private Region regionOf(Course course) {
        return regionRepository.findByIds(List.of(course.getRegionId())).stream()
                .findFirst()
                .orElse(null);
    }

    private GeneratedCourse assemble(Course course, Region region, TrainAccess trainAccess) {
        // 혜택은 **여행일** 기준으로 매칭한다(#213). 정책에 유효기간이 있어 기준일이 결과를 가르는데,
        // 여기만 오늘을 넘기고 있어 생성 응답과 상세 조회의 혜택이 어긋났다 — 저장하는 순간부터 갈리고
        // 여행이 멀수록 벌어졌다. 날짜 없이 저장된 코스는 알 수 없어 오늘로 물러선다.
        LocalDate benefitDate = course.travelDateOr(LocalDate.now(SERVICE_ZONE));
        List<GeneratedCourse.Benefit> benefits = policyService.matchForRegion(course.getRegionId(), benefitDate)
                .stream()
                .map(policy -> new GeneratedCourse.Benefit(policy.getId(), policy.getType(), policy.badgeText()))
                .toList();
        // 저장 코스에도 날씨를 붙인다(#169) — 생성 응답에만 실리고 상세 조회에는 없어 화면이 비어 있었다.
        // 날짜를 안 넣고 저장한 코스는 물어볼 기준이 없어 그대로 빈다.
        Map<Integer, DailyWeather> weatherByDay =
                courseWeatherProvider.byDay(course, region, course.center().orElse(null));
        // 생성 응답에만 붙고 상세 조회에는 없으면 저장한 코스에서 값이 사라진다(#169 와 같은 실수).
        // 공유 토큰은 조립이 아니라 영속 경계에서 온다 — 필요한 호출자가 withShareToken 으로 얹는다(#143).
        return new GeneratedCourse(
                course, benefits, weatherByDay, trainAccess, region == null ? null : region.getSigungu(),
                // 받아 둔 것만 읽는다 — 요청 경로에서 외부를 부르지 않는다(#157). 아직 없으면 그 줄이 빈다.
                openingHoursProvider.forCourse(course), null, null);
    }
}
