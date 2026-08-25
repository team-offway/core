package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.TripOutcome;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** 도메인이 의존하는 port. 구현은 {@link TripOutcomeRepositoryImpl}. */
public interface TripOutcomeRepository {

    /**
     * 탈퇴 — 이 소유자의 여행 후기 응답을 전부 지운다.
     *
     * @return 지운 행 수
     */
    int deleteByUserId(UUID userId);

    /**
     * 이 사람의 이 코스 답변을 지운다 — <b>"답하지 않은 상태" 로 되돌린다</b>(#327).
     *
     * <p>차감을 취소하면 내역만 사라지고 답변은 남아, 홈 모달이 다시 묻지 않는다. 앱에는 모달 말고
     * 차감하는 길이 없어서(#288 로 일원화) 그 코스는 영영 미방문으로 굳는다.
     *
     * @return 지운 행 수. 답한 적 없으면 0 — 취소는 멱등이라 그것도 정상이다
     */
    int deleteAnswer(UUID userId, long courseId);


    /**
     * 이 소유자가 이미 답한 코스 ID 들 — 홈 모달의 대기 목록에서 걸러낼 대상.
     *
     * <p>코스마다 "답했나" 를 물으면 코스 수만큼 쿼리가 늘어난다. 한 번에 모아온다.
     */
    Set<Long> findAnsweredCourseIds(UUID userId);

    /** 이 코스들 중 이미 답한 것 — 알림 배치가 소유자별로 묻지 않게 한 번에 판다(#302). */
    Set<Long> findAnsweredCourseIdsIn(Collection<Long> courseIds);

    TripOutcome save(TripOutcome outcome);
}
