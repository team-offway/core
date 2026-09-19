package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.RegionFeedback;

/** 익명 여행지 평가 저장 port(#592). 구현은 {@link RegionFeedbackRepositoryImpl}. */
public interface RegionFeedbackRepository {

    /**
     * 평가 한 건을 쌓는다.
     *
     * <p><b>조회가 없다.</b> 지금은 쌓기만 하고, 지역별 집계는 실제로 쌓인 뒤에 만든다(#592 범위 밖) —
     * 지금 만들면 어떤 모양으로 볼지 모르는 채 추측으로 짓는다. 쓰이지 않을 조회를 미리 열지 않는다.
     */
    RegionFeedback save(RegionFeedback feedback);
}
