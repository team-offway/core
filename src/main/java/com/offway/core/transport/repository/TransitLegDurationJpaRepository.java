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
     *
     * <p><b>그중에서도 실제로 물어본 것이 먼저다(#491).</b> 예전에는 {@code requestedAt} 이 오래된 순이었는데,
     * #450 이 후보 33,448건을 부팅 시각으로 한꺼번에 넣으면서 <b>사용자가 방금 요청한 구간이 3만 건 뒤에
     * 섰다.</b> 배치가 시간당 50구간이라 28일이 걸린다 — 정작 화면에 값이 없는 구간이 가장 늦게 채워졌다.
     *
     * <p>아무도 안 물어본 시드는 뒤에 둔다. 버리지는 않는다 — 노는 시간에 채워지면 그만큼 이득이다.
     */
    @Query("""
            select l from TransitLegDuration l
            where l.measuredAt is null
               or (l.minutes is null and l.measuredAt < :remeasureBefore)
            order by case when l.measuredAt is null then 0 else 1 end,
                     case when l.lastAskedAt is null then 1 else 0 end,
                     l.lastAskedAt desc,
                     l.requestedAt
            """)
    List<TransitLegDuration> findPending(@Param("remeasureBefore") LocalDateTime remeasureBefore, Limit limit);
}
