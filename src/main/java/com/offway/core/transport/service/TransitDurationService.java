package com.offway.core.transport.service;

import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.repository.TransitLegDurationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 버스·여객선 구간 소요시간의 저장소 쪽 창구(#107 · #97). <b>외부를 부르지 않는다</b> — 여기는 요청 경로에서
 * 쓰이므로 DB 만 본다. 실제 측정은 {@link TransitDurationRefreshService} 가 배치로 한다.
 *
 * <p>값이 없으면 자리만 만들어 둔다({@link #minutesFor}). 그 자리를 배치가 보고 채운다 — 미리 전부 재지 않고
 * <b>쓰다가 필요해진 짝만</b> 쌓는 방식이라 초기 적재가 없다({@code unroutable_probe} 와 같은 판단).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitDurationService {

    private final TransitLegDurationRepository transitLegDurationRepository;

    /**
     * 이 구간의 소요시간(분). 아직 안 쟀으면 <b>자리를 만들고</b> 빈 값을 준다.
     *
     * <p>자리 만들기가 실패해도 조회는 성공으로 답한다 — 소요시간은 코스의 곁가지고, 여기서 예외가 올라가면
     * 기록 한 줄을 못 적은 일이 코스 응답 전체를 실패시킨다.
     */
    @Transactional
    public Optional<Integer> minutesFor(TransitMode mode, String depCode, String arrCode, LocalDateTime now) {
        if (mode == TransitMode.TRAIN) {
            // 열차는 실제 시각을 직접 답하므로 이 표를 쓰지 않는다. 넣어 두면 안 쓰이는 행만 쌓인다.
            return Optional.empty();
        }
        Optional<TransitLegDuration> found = transitLegDurationRepository.find(mode, depCode, arrCode);
        if (found.isPresent()) {
            return found.get().usableMinutes();
        }
        try {
            transitLegDurationRepository.requestIfAbsent(
                    TransitLegDuration.requested(mode, depCode, arrCode, now));
        } catch (RuntimeException e) {
            log.warn("구간 측정 요청 기록 실패 — 코스는 그대로 진행한다 {} {}→{}", mode, depCode, arrCode, e);
        }
        return Optional.empty();
    }

    /**
     * 배치가 잴 대상 {@code max} 건 — 아직 안 잰 구간이 먼저고, 그다음이 미운행으로 적힌 지 오래된 구간이다.
     *
     * <p><b>미운행도 다시 잰다.</b> 한 번의 조회로 굳히면 겨울에 쉬는 항로·새로 뚫린 노선이 영원히 없는 길이
     * 된다 — 배 말고 닿는 수단이 없는 지역에서는 그게 곧 "도달 불가" 다.
     *
     * @param remeasureBefore 이 시각 이전에 미운행으로 적힌 구간을 다시 잴 대상에 넣는다
     */
    @Transactional(readOnly = true)
    public List<TransitLegDuration> pending(int max, LocalDateTime remeasureBefore) {
        return transitLegDurationRepository.pending(max, remeasureBefore);
    }

    /**
     * 측정 결과를 적는다. {@code leg} 가 null 이면 "재봤더니 운행이 없다" 는 뜻이고 그것도 결과다 —
     * 그렇게 적어야 배치가 같은 구간을 영원히 다시 재지 않는다.
     *
     * <p><b>별도 빈이 아니라 이 빈의 메서드인 이유</b>: 호출자({@link TransitDurationRefreshService})가
     * 다른 빈이라 프록시를 거친다. 같은 빈 안에서 부르면 self-invocation 이라 트랜잭션이 안 걸린다.
     */
    @Transactional
    public void record(TransitMode mode, String depCode, String arrCode, MeasuredLeg leg, LocalDateTime now) {
        transitLegDurationRepository.find(mode, depCode, arrCode).ifPresent(row -> {
            row.measured(leg, now);
            transitLegDurationRepository.save(row);
        });
    }
}
