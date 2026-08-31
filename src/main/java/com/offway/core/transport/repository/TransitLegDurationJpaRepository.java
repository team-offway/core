package com.offway.core.transport.repository;

import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitLegDurationJpaRepository extends JpaRepository<TransitLegDuration, Long> {

    Optional<TransitLegDuration> findByModeAndDepCodeAndArrCode(TransitMode mode, String depCode, String arrCode);

    /** 아직 안 잰 구간 — 오래 기다린 것부터. 한 번에 가져올 수를 호출부가 정한다(한도 보호). */
    List<TransitLegDuration> findByMeasuredAtIsNullOrderByRequestedAtAsc(Limit limit);
}
