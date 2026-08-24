package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseShare;
import com.offway.core.itinerary.domain.DayStart;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.repository.CourseShareRepository;
import com.offway.core.leave.service.MyLeaveService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 영속만 담당하는 별도 빈 — <b>트랜잭션 경계를 쪼개려고</b> 분리했다.
 *
 * <p>동시 삭제 충돌은 {@code delete()} 호출 시점이 아니라 <b>flush·commit 시점</b>에 드러난다. 같은 트랜잭션 안에서
 * 잡으려 하면 예외가 이미 그 트랜잭션을 못 쓰게 만든 뒤라 소용이 없고, 삼켜도 커밋에서
 * {@code UnexpectedRollbackException} 으로 끝난다. 경계를 나눠 <b>커밋까지 끝난 결과</b>를 호출자가 받게 한다.
 *
 * <p>같은 빈 안에서 나눠도 소용없다 — self-invocation 은 프록시를 거치지 않는다(persistence-convention).
 */
@Service
@RequiredArgsConstructor
public class CoursePersistenceService {

    private final CourseRepository courseRepository;
    private final CourseShareRepository courseShareRepository;
    private final MyLeaveService myLeaveService;

    /**
     * 소유자 범위에서 찾아 지우고, <b>이 코스로 깎았던 연차도 함께 되돌린다</b>(#113).
     *
     * <p>둘을 나누면 "코스는 사라졌는데 연차는 깎인 채" 가 남는다. 그 상태는 코스가 없어 취소 API 로도 못 고친다.
     * 그래서 한 트랜잭션이다 — {@code cancelCourseDeduction} 이 기본 전파라 이 트랜잭션에 합류한다.
     *
     * <p>없거나 남의 코스면 404.
     *
     * <p>동시에 같은 코스를 지우면 두 요청이 <b>둘 다 조회에 성공</b>하고, 뒤늦은 쪽이 커밋에서 "지울 행이 없다" 로
     * 실패한다({@code OptimisticLockingFailureException}). 그 번역은 호출자가 한다 — 여기서 잡으면 이미 늦다.
     */
    /**
     * 소유자의 저장 코스를 읽는다 — <b>트랜잭션은 여기까지</b>(#169).
     *
     * <p>혜택·지역·날씨 조립은 호출자가 트랜잭션 밖에서 한다. 날씨는 외부 호출이라 read-timeout 이 길고,
     * 트랜잭션 안에 넣으면 DB 커넥션을 그만큼 오래 잡는다(영속성 규약).
     *
     * <p>없거나 소유자가 아니면 존재 여부를 흘리지 않도록 똑같이 404.
     */
    @Transactional(readOnly = true)
    public Course loadOwned(UUID userId, long courseId) {
        Course course = courseRepository
                .findByIdAndUserId(courseId, userId)
                .orElseThrow(ItineraryException::courseNotFound);
        course.totalSlots(); // tx 안에서 days·slots 초기화(직렬화·조립은 tx 밖)
        return course;
    }

    /**
     * 코스를 저장한다 — <b>트랜잭션은 여기까지</b>.
     *
     * <p>{@link #loadOwned} 와 같은 이유다. 저장 뒤에 붙는 혜택·날씨 조립은 외부 호출을 포함하므로
     * 트랜잭션 안에서 하면 DB 커넥션을 그 시간만큼 잡는다(영속성 규약). 기상청 단기예보만 해도 실측
     * 1.15초짜리를 Day 수만큼 부른다.
     *
     * <p>예전에는 {@code save()} 가 통째로 {@code @Transactional} 이었다. 열차 접근만 빼면 된다고 봤는데,
     * 같은 조립 안에 날씨가 있어 커넥션을 잡은 채 외부를 기다리고 있었다.
     */
    @Transactional
    public Course persist(Course course) {
        Course saved = courseRepository.save(course);
        saved.totalSlots(); // tx 안에서 days·slots 초기화(직렬화·조립은 tx 밖)
        return saved;
    }

    /**
     * 주인 없는 코스와 그 공유 링크를 <b>한 트랜잭션으로</b> 저장한다(#261).
     *
     * <p><b>나눠 저장하면 아무도 닿을 수 없는 행이 남는다.</b> 코스만 커밋되고 링크 발급이 실패하면, 그
     * 코스는 주인이 없어 목록·상세·삭제 어디에도 안 걸리고 공유 행이 없어 링크로도 못 연다. 나중에 붙일
     * 정리는 <b>공유 행의 발급 시각으로 나이를 재므로</b>(코스 테이블에 생성 시각이 없다) 그 정리조차 이
     * 행을 못 찾는다 — 영영 남는 죽은 데이터다.
     *
     * <p>"담지 않은 코스는 반드시 공유 행과 짝" 이라는 정리의 전제를 여기서 지킨다.
     *
     * <p>여기서는 발급 경합을 다루지 않는다({@link #shareOf} 와 다른 점이다) — 방금 만든 코스라 그 id 를
     * 아는 요청이 하나뿐이고, 유니크 제약에 걸릴 상대가 없다.
     */
    @Transactional
    public CourseShare persistWithShare(Course course) {
        Course saved = courseRepository.save(course);
        return courseShareRepository.save(CourseShare.issue(saved.getId(), LocalDateTime.now()));
    }

    /**
     * 공유 토큰으로 코스를 읽는다(#143) — <b>소유자 확인 없이</b>. 링크를 받은 사람에게는 우리 계정이 없다.
     *
     * <p>토큰과 코스를 나눠 판정한다. 토큰이 없으면 잘못된 링크(404)지만, 토큰은 있는데 코스가 없으면
     * <b>게시자가 지운 것</b>(410)이다. 받은 사람이 "내가 링크를 잘못 눌렀나" 와 "게시자가 지웠구나" 를
     * 구분할 수 있어야 한다.
     */
    @Transactional(readOnly = true)
    public Course loadShared(String shareToken) {
        CourseShare share = courseShareRepository
                .findByShareToken(shareToken)
                .orElseThrow(ItineraryException::shareNotFound);
        Course course = courseRepository
                .findById(share.getCourseId())
                .orElseThrow(ItineraryException::shareCourseDeleted);
        course.totalSlots(); // tx 안에서 days·slots 초기화(직렬화·조립은 tx 밖)
        return course;
    }

    /**
     * 여행 날짜를 옮기고, <b>이 코스로 깎았던 연차도 같은 트랜잭션에서 다시 잡는다</b>(#170).
     *
     * <p>둘을 나누면 "날짜는 옮겨졌는데 차감은 옛 날짜 기준" 이 남는다. 사용자는 화면에서 새 날짜를 보면서
     * 옛 날짜의 평일 수만큼 연차가 깎인 상태가 되는데, 어긋난 줄 알 방법이 없다 —
     * {@code deleteOwned} 가 삭제와 차감 취소를 한 덩어리로 묶은 것과 같은 이유다.
     *
     * <p>코스를 여기서 다시 읽는다. 호출자가 트랜잭션 밖에서 읽은 인스턴스는 준영속이라 변경 감지가 안 되고,
     * 그 사이 코스가 지워졌을 수도 있다.
     *
     * @param deductionDays 다시 계산한 차감 일수. 차감한 적 없는 코스면 null — 연차는 건드리지 않는다
     */
    @Transactional
    public Course applyTravelDate(UUID userId, long courseId, LocalDate travelDate, Double deductionDays) {
        Course course = courseRepository
                .findByIdAndUserId(courseId, userId)
                .orElseThrow(ItineraryException::courseNotFound);
        course.changeTravelDate(travelDate, LocalDate.now(CourseStorageService.SERVICE_ZONE));
        if (deductionDays != null) {
            myLeaveService.rescheduleCourseDeduction(userId, courseId, travelDate, deductionDays);
        }
        course.totalSlots(); // tx 안에서 days·slots 초기화(직렬화·조립은 tx 밖)
        return course;
    }

    /**
     * 새 날짜의 도착 시각으로 <b>첫날을 다시 맞춘다</b>(#214) — 갈 수 없게 된 슬롯을 걷어낸다.
     *
     * <p>날짜 수정은 열차 도착 시각을 새 날짜로 다시 조회하는데, 그 시각으로 내린 일정 판단은 저장된 옛것이
     * 남는다. 그래서 도착 전 시간에 일정이 잡힌 코스가 만들어진다 — 화면상 멀쩡한데 갈 수 없다.
     *
     * <p>판단은 도메인({@code Course#trimFirstDayTo})이 하고 여기서는 트랜잭션만 연다. 애그리거트를 고치므로
     * 변경 감지가 필요하고, 그러려면 로딩과 수정이 같은 트랜잭션이어야 한다.
     *
     * @return 걷어낸 슬롯 수. 0 이면 바뀐 것이 없다
     */
    @Transactional
    public int realignFirstDay(UUID userId, long courseId, DayStart firstDayStart) {
        Course course = courseRepository
                .findByIdAndUserId(courseId, userId)
                .orElseThrow(ItineraryException::courseNotFound);
        return course.trimFirstDayTo(firstDayStart);
    }

    /**
     * 코스의 공유 토큰 — 없으면 발급하고, 있으면 그대로 준다.
     *
     * <p><b>멱등해야 한다.</b> 공유 버튼을 두 번 눌렀다고 링크가 바뀌면, 먼저 뿌린 링크가 죽는다.
     * 코스당 하나라는 것은 유니크 제약이 지킨다.
     */
    @Transactional
    public CourseShare shareOf(Long courseId) {
        return courseShareRepository
                .findByCourseId(courseId)
                .orElseGet(() -> courseShareRepository.save(CourseShare.issue(courseId, LocalDateTime.now())));
    }

    /**
     * 이미 발급된 공유 링크 — 없으면 빈 값.
     *
     * <p>{@link #shareOf} 가 유니크 제약에 걸렸을 때 <b>먼저 만든 쪽의 것</b>을 다시 읽으려고 있다.
     * 제약 위반은 그 트랜잭션을 rollback-only 로 만들므로 같은 트랜잭션 안에서는 다시 읽을 수 없다 —
     * 별도 빈의 새 트랜잭션이어야 한다.
     */
    @Transactional(readOnly = true)
    public Optional<CourseShare> findShare(Long courseId) {
        return courseShareRepository.findByCourseId(courseId);
    }

    /**
     * 여러 코스의 공유 토큰 — <b>한 번의 조회로</b>(#259). 목록 응답이 쓴다.
     *
     * <p><b>여기서는 발급하지 않는다.</b> 목록은 페이지당 최대 100건이라, 없는 것을 채우려 들면 조회 한 번이
     * 최대 100번의 INSERT 가 된다. 아직 링크가 없는 코스는 상세를 열 때 그 자리에서 발급된다
     * ({@link CourseStorageService#get}).
     *
     * @return 코스 ID → 토큰. 링크가 없는 코스는 <b>키가 없다</b>
     */
    @Transactional(readOnly = true)
    public Map<Long, String> shareTokensOf(Collection<Long> courseIds) {
        return courseShareRepository.findByCourseIdIn(courseIds).stream()
                .collect(Collectors.toMap(CourseShare::getCourseId, CourseShare::getShareToken, (a, b) -> a));
    }

    @Transactional
    public void deleteOwned(UUID userId, long courseId) {
        Course course = courseRepository
                .findByIdAndUserId(courseId, userId)
                .orElseThrow(ItineraryException::courseNotFound);
        myLeaveService.cancelCourseDeduction(userId, courseId);
        courseRepository.delete(course);
    }
}
