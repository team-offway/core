package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseShare;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.repository.CourseShareRepository;
import com.offway.core.leave.service.MyLeaveService;
import java.time.LocalDateTime;
import java.util.Optional;
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
     * 게스트 소유의 저장 코스를 읽는다 — <b>트랜잭션은 여기까지</b>(#169).
     *
     * <p>혜택·지역·날씨 조립은 호출자가 트랜잭션 밖에서 한다. 날씨는 외부 호출이라 read-timeout 이 길고,
     * 트랜잭션 안에 넣으면 DB 커넥션을 그만큼 오래 잡는다(영속성 규약).
     *
     * <p>없거나 소유자가 아니면 존재 여부를 흘리지 않도록 똑같이 404.
     */
    @Transactional(readOnly = true)
    public Course loadOwned(String guestId, long courseId) {
        Course course = courseRepository
                .findByIdAndGuestId(courseId, guestId)
                .orElseThrow(ItineraryException::courseNotFound);
        course.totalSlots(); // tx 안에서 days·slots 초기화(직렬화·조립은 tx 밖)
        return course;
    }

    /**
     * 코스를 저장한다 — <b>트랜잭션은 여기까지</b>.
     *
     * <p>{@link #loadOwned} 와 같은 이유다. 저장 뒤에 붙는 혜택·날씨·대기질 조립은 외부 호출을 포함하므로
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

    @Transactional
    public void deleteOwned(String guestId, long courseId) {
        Course course = courseRepository
                .findByIdAndGuestId(courseId, guestId)
                .orElseThrow(ItineraryException::courseNotFound);
        myLeaveService.cancelCourseDeduction(guestId, courseId);
        courseRepository.delete(course);
    }
}
