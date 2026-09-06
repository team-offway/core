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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
    public List<TransitLegDuration> findAll() {
        return transitLegDurationJpaRepository.findAll();
    }

    @Override
    public List<TransitLegDuration> pending(int max, LocalDateTime remeasureBefore) {
        return transitLegDurationJpaRepository.findPending(remeasureBefore, Limit.of(max));
    }

    @Override
    public void save(TransitLegDuration leg) {
        transitLegDurationJpaRepository.save(leg);
    }

    /**
     * {@code INSERT IGNORE} 로 넣는다 — 중복이 예외가 아니라 <b>0행</b>이 된다.
     *
     * <p>JPA 로는 이 의미를 낼 수 없다. {@code save} 는 중복에서 예외를 던지고, 그 예외가 트랜잭션을
     * {@code rollback-only} 로 만들어 같은 조각의 새 구간까지 함께 사라진다.
     *
     * <p>배치로 밀어 왕복을 줄인다 — 조각이 1,000건이라 건별 왕복이면 그만큼 느려진다.
     */
    @Override
    public int insertIgnoringDuplicates(List<TransitLegDuration> legs) {
        if (legs.isEmpty()) {
            return 0;
        }
        int[][] results = jdbcTemplate.batchUpdate(
                """
                INSERT IGNORE INTO transit_leg_duration (mode, dep_code, arr_code, requested_at)
                VALUES (?, ?, ?, ?)
                """,
                legs,
                legs.size(),
                (ps, leg) -> {
                    ps.setString(1, leg.getMode().name());
                    ps.setString(2, leg.getDepCode());
                    ps.setString(3, leg.getArrCode());
                    ps.setTimestamp(4, java.sql.Timestamp.valueOf(leg.getRequestedAt()));
                });
        return java.util.Arrays.stream(results).flatMapToInt(java.util.Arrays::stream).sum();
    }
}
