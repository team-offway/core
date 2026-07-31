package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.TripOutcome;
import java.util.Set;

/** 도메인이 의존하는 port. 구현은 {@link TripOutcomeRepositoryImpl}. */
public interface TripOutcomeRepository {

    /**
     * 이 소유자가 이미 답한 코스 ID 들 — 홈 모달의 대기 목록에서 걸러낼 대상.
     *
     * <p>코스마다 "답했나" 를 물으면 코스 수만큼 쿼리가 늘어난다. 한 번에 모아온다.
     */
    Set<Long> findAnsweredCourseIds(String guestId);

    TripOutcome save(TripOutcome outcome);
}
