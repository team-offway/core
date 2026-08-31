package com.offway.core.transport.repository;

import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransitLegDurationJpaRepository extends JpaRepository<TransitLegDuration, Long> {

    Optional<TransitLegDuration> findByModeAndDepCodeAndArrCode(TransitMode mode, String depCode, String arrCode);

    /**
     * 배치가 잴 구간 — 아직 안 잰 것과, 미운행으로 적힌 지 오래된 것.
     *
     * <p><b>안 잰 것이 먼저다.</b> 그쪽은 사용자의 코스가 지금 소요시간 없이 나가고 있다는 뜻이라, 다시 재는
     * 일보다 급하다.
     */
    @Query("""
            select l from TransitLegDuration l
            where l.measuredAt is null
               or (l.minutes is null and l.measuredAt < :remeasureBefore)
            order by case when l.measuredAt is null then 0 else 1 end, l.requestedAt
            """)
    List<TransitLegDuration> findPending(@Param("remeasureBefore") LocalDateTime remeasureBefore, Limit limit);
}
