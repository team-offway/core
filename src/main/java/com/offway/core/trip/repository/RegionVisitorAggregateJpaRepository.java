package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionVisitorAggregate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RegionVisitorAggregateJpaRepository extends JpaRepository<RegionVisitorAggregate, Long> {

    /**
     * 기준 연월이 가장 큰 행 하나.
     *
     * <p>{@code base_ym} 은 {@code yyyyMM} 고정폭이라 <b>문자열 내림차순 = 시간 역순</b>이다.
     */
    Optional<RegionVisitorAggregate> findTopByOrderByBaseYmDesc();
}
