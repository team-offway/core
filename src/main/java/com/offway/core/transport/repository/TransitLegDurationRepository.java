package com.offway.core.transport.repository;

import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 도메인이 의존하는 port. 구현은 {@link TransitLegDurationRepositoryImpl}. */
public interface TransitLegDurationRepository {

    Optional<TransitLegDuration> find(TransitMode mode, String depCode, String arrCode);

    /**
     * 없을 때만 자리를 만든다.
     *
     * <p>같은 구간을 두 번 적으면 배치가 같은 것을 두 번 잰다 — 그만큼 외부 한도가 샌다.
     */
    void requestIfAbsent(TransitLegDuration leg);

    /**
     * 배치가 잴 구간을 {@code max} 건까지. 아직 안 잰 것이 먼저고, 그다음이 다시 잴 것이다.
     *
     * @param remeasureBefore 이 시각 이전에 미운행으로 적힌 구간은 다시 잰다 — 계절 항로·신설 노선이
     *     한 번의 조회로 영원히 굳지 않게 한다
     */
    List<TransitLegDuration> pending(int max, LocalDateTime remeasureBefore);

    void save(TransitLegDuration leg);
}
