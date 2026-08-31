package com.offway.core.transport.repository;

import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TransitLegDurationRepositoryImpl implements TransitLegDurationRepository {

    private final TransitLegDurationJpaRepository transitLegDurationJpaRepository;

    @Override
    public Optional<TransitLegDuration> find(TransitMode mode, String depCode, String arrCode) {
        return transitLegDurationJpaRepository.findByModeAndDepCodeAndArrCode(mode, depCode, arrCode);
    }

    @Override
    public void requestIfAbsent(TransitLegDuration leg) {
        if (transitLegDurationJpaRepository
                .findByModeAndDepCodeAndArrCode(leg.getMode(), leg.getDepCode(), leg.getArrCode())
                .isPresent()) {
            return;
        }
        try {
            transitLegDurationJpaRepository.save(leg);
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 다른 요청이 같은 구간을 넣었다. 원하던 상태가 이미 됐으므로 성공이다.
            log.debug("이미 등록된 구간입니다 — 무시합니다");
        }
    }

    @Override
    public List<TransitLegDuration> pending(int max, LocalDateTime remeasureBefore) {
        return transitLegDurationJpaRepository.findPending(remeasureBefore, Limit.of(max));
    }

    @Override
    public void save(TransitLegDuration leg) {
        transitLegDurationJpaRepository.save(leg);
    }
}
