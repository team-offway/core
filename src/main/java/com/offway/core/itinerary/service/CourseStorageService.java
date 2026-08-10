package com.offway.core.itinerary.service;

import com.offway.core.common.response.Paging;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseScope;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
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

    private final CourseRepository courseRepository;
    private final PolicyService policyService;
    private final RegionRepository regionRepository;
    private final CourseWeatherProvider courseWeatherProvider;
    private final CourseAirQualityProvider courseAirQualityProvider;
    private final CoursePersistenceService coursePersistenceService;
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
                        log.error("공유 링크 발급 실패 — 중복이 아닌 제약 위반 courseId={}", courseId, e);
                        return e;
                    });
        }
    }

    /**
     * 공유 링크로 여는 코스(#143) — <b>인증 없이, 소유자 확인 없이</b>.
     *
     * <p>접근을 막는 것은 소유자가 아니라 추측 불가능한 토큰이다. 그래서 이 경로는 소유자 식별자를 절대
     * 반환하지 않는다 — 응답 조립은 {@link CourseResponse#publicView} 가 내부 식별자를 걷어낸다.
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
        return MyCourses.from(found, myLeaveService.deductedCourseIds(guestId), regionNamesOf(courses), today);
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
        return withBenefits(course, true);
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

    private GeneratedCourse withBenefits(Course course, boolean withTrainAccess) {
        List<GeneratedCourse.Benefit> benefits = policyService.matchForRegion(course.getRegionId(), LocalDate.now())
                .stream()
                .map(policy -> new GeneratedCourse.Benefit(policy.getId(), policy.getType(), policy.badgeText()))
                .toList();
        // 지역명은 슬롯마다 "관광명소 · 정선군" 으로 붙어 저장 코스에도 필요하다(#141).
        Region region = regionRepository.findByIds(List.of(course.getRegionId())).stream()
                .findFirst()
                .orElse(null);
        // 저장 코스에도 날씨를 붙인다(#169) — 생성 응답에만 실리고 상세 조회에는 없어 화면이 비어 있었다.
        // 날짜를 안 넣고 저장한 코스는 물어볼 기준이 없어 그대로 빈다.
        Map<Integer, DailyWeather> weatherByDay =
                courseWeatherProvider.byDay(course, region, course.center().orElse(null));
        TrainAccess trainAccess = withTrainAccess ? trainAccessFor(course, region) : null;
        // 생성 응답에만 붙고 상세 조회에는 없으면 저장한 코스에서 값이 사라진다(#169 와 같은 실수).
        // 공유 토큰은 조립이 아니라 영속 경계에서 온다 — 필요한 호출자가 withShareToken 으로 얹는다(#143).
        return new GeneratedCourse(
                course, benefits, weatherByDay, trainAccess, region == null ? null : region.getSigungu(),
                courseAirQualityProvider.of(course, region), null);
    }
}
