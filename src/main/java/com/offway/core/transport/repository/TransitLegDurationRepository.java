package com.offway.core.transport.repository;

import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
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

    /** 아직 안 잰 구간을 오래 기다린 순으로. {@code max} 로 한 번에 가져올 수를 제한한다. */
    List<TransitLegDuration> unmeasured(int max);

    void save(TransitLegDuration leg);
}
