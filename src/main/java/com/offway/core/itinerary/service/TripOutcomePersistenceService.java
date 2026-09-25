package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.RegionFeedback;
import com.offway.core.itinerary.domain.TripFeedback;
import com.offway.core.itinerary.domain.TripOutcome;
import com.offway.core.itinerary.domain.VisitOutcome;
import com.offway.core.itinerary.repository.RegionFeedbackRepository;
import com.offway.core.itinerary.repository.TripOutcomeRepository;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 결과와 익명 평가를 <b>한 트랜잭션에</b> 쓴다(#592).
 *
 * <h2>왜 빈을 갈랐나</h2>
 *
 * 답을 받는 흐름은 차감 계산이 공휴일(외부) 조회를 타서 {@link TripOutcomeService} 가 트랜잭션을 걸지
 * 않는다. 그런데 이제 <b>쓰는 표가 둘</b>이다 — 답과 평가. 둘이 따로 커밋되면 답은 남았는데 평가만
 * 사라지거나(사용자는 남겼다고 여긴다), 평가는 쌓였는데 답이 없어 <b>모달이 다시 물으면서 평가가 또
 * 쌓인다</b>. 후자가 더 나쁘다 — 집계가 조용히 부풀어 오른다.
 *
 * <p>그래서 외부 호출을 트랜잭션 밖에서 끝낸 뒤 <b>영속화만 이 빈에 위임</b>한다(persistence-convention).
 *
 * <p><b>같은 빈 안에서 부를 수 없다.</b> {@code @Transactional} 메서드를 같은 클래스에서 직접 부르면
 * Spring AOP proxy 를 거치지 않아 트랜잭션이 조용히 무력화된다. 경계를 나누려면 빈을 나눠야 한다 —
 * {@code UserWithdrawalPersistenceService} 가 같은 이유로 갈라져 있다.
 */
@Service
@RequiredArgsConstructor
public class TripOutcomePersistenceService {

    private final TripOutcomeRepository tripOutcomeRepository;
    private final RegionFeedbackRepository regionFeedbackRepository;

    /**
     * 답을 기록하고, 평가가 있으면 익명으로 함께 쌓는다.
     *
     * <p><b>답을 먼저 쓴다.</b> 코스당 하나라는 유니크 제약이 거기 붙어 있어, 중복 제출이면 그 시점에
     * 터진다 — 평가가 먼저 들어가면 그 제약이 막아 주기 전에 집계가 한 건 늘어난다. 같은 트랜잭션이라
     * 롤백되긴 하지만, 순서를 맞춰 두면 그 롤백에 기대지 않아도 된다.
     *
     * <p><b>빈 평가는 행을 만들지 않는다.</b> 건너뛴 사람의 빈 행이 쌓이면 지역별 집계가 실제보다 많은
     * 의견이 있는 것처럼 보인다.
     *
     * @param regionId 평가가 향하는 지역 — 코스에서 읽어 넘긴다
     */
    @Transactional
    public void record(
            UUID userId,
            long courseId,
            long regionId,
            VisitOutcome outcome,
            LocalDate answeredOn,
            TripFeedback feedback) {
        tripOutcomeRepository.save(TripOutcome.of(userId, courseId, outcome, answeredOn));
        if (feedback.isPresent()) {
            regionFeedbackRepository.save(RegionFeedback.of(regionId, feedback, answeredOn));
        }
    }
}
