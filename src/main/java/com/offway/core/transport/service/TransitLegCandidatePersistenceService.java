package com.offway.core.transport.service;

import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.repository.TransitLegDurationRepository;
import com.offway.core.transport.service.TransitLegCandidateSeeder.Candidate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구간 후보 적재의 <b>트랜잭션 경계</b>(#450) — 판단은 {@link TransitLegCandidateSeeder} 가 한다.
 *
 * <p><b>왜 별도 빈인가.</b> 같은 빈 안에서 {@code @Transactional} 메서드를 부르면 프록시를 안 거쳐
 * 트랜잭션이 무력화된다(영속성 규약). 그리고 3만 건을 한 트랜잭션에 담으면 커밋까지 락과 undo 로그를
 * 오래 들고 있게 되어, <b>조각마다 끊는다</b>.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitLegCandidatePersistenceService {

    private final TransitLegDurationRepository transitLegDurationRepository;

    /**
     * 이미 있는 구간의 키 — {@code mode|dep|arr}.
     *
     * <p><b>건별로 묻지 않는다.</b> 후보가 3만 건이라 {@code existsBy} 를 그만큼 부르면 부팅이 그 왕복
     * 수만큼 길어진다. 한 번에 읽어 메모리에서 뺀다 — 행이 작아(키 셋뿐) 감당된다.
     */
    @Transactional(readOnly = true)
    public Set<String> existingKeys() {
        return transitLegDurationRepository.findAll().stream()
                .map(leg -> leg.getMode() + "|" + leg.getDepCode() + "|" + leg.getArrCode())
                .collect(Collectors.toSet());
    }

    /**
     * 조각 하나를 넣는다.
     *
     * <p><b>유니크 위반은 예외가 아니라 0행이다</b>({@code INSERT IGNORE}). 사이에 사용자 요청이 같은
     * 구간을 만들었을 수 있는데, 원하던 상태(그 구간의 자리가 있다)는 이미 이뤄져 있다.
     *
     * <p>예전에는 {@code save} 로 넣고 {@code DataIntegrityViolationException} 을 잡았다. <b>그것으로는
     * 안 된다</b> — 그 예외가 트랜잭션을 {@code rollback-only} 로 만들어, 중복 한 건 때문에 같은 조각의
     * 새 구간 수백 건이 함께 사라진다. 커밋 시점에 터지면 잡을 수조차 없다.
     *
     * <p><b>시드는 "물어본 것" 이 아니다</b>({@code seeded}). 같은 자격으로 넣으면 사용자가 방금 요청한
     * 구간이 3만 건 뒤에 서서 배치 주기로 28일을 기다린다(#491).
     *
     * @return 실제로 넣은 행 수
     */
    @Transactional
    public int insert(List<Candidate> candidates, LocalDateTime now) {
        return transitLegDurationRepository.insertIgnoringDuplicates(candidates.stream()
                .map(candidate -> TransitLegDuration.seeded(
                        candidate.mode(), candidate.depCode(), candidate.arrCode(), now))
                .toList());
    }
}
