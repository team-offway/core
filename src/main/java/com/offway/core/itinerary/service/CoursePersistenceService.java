package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.repository.CourseRepository;
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

    /**
     * 소유자 범위에서 찾아 지운다. 없거나 남의 코스면 404.
     *
     * <p>동시에 같은 코스를 지우면 두 요청이 <b>둘 다 조회에 성공</b>하고, 뒤늦은 쪽이 커밋에서 "지울 행이 없다" 로
     * 실패한다({@code OptimisticLockingFailureException}). 그 번역은 호출자가 한다 — 여기서 잡으면 이미 늦다.
     */
    @Transactional
    public void deleteOwned(String guestId, long courseId) {
        Course course = courseRepository
                .findByIdAndGuestId(courseId, guestId)
                .orElseThrow(ItineraryException::courseNotFound);
        courseRepository.delete(course);
    }
}
