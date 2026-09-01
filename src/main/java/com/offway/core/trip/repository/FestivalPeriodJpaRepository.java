package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPeriod;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface FestivalPeriodJpaRepository extends JpaRepository<FestivalPeriod, String> {

    /**
     * 이 contentId 들의 기간을 한 번에 읽는다.
     *
     * <p><b>한 건씩 읽지 않는다.</b> 코스 후보가 수십 건이라 그러면 조회가 후보 수만큼 돈다 — 요청
     * 경로에서 도는 질의라 그 곱셈이 그대로 사용자 대기가 된다.
     */
    List<FestivalPeriod> findByContentIdIn(Collection<String> contentIds);
}
