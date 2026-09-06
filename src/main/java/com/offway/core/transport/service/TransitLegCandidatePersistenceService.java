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
import org.springframework.dao.DataIntegrityViolationException;
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
     * <p><b>유니크 위반은 성공으로 본다.</b> 사이에 사용자 요청이 같은 구간을 만들었을 수 있는데, 원하던
     * 상태(그 구간의 자리가 있다)는 이미 이뤄져 있다. 조각째 버리지 않고 건별로 넘긴다 — 하나 때문에
     * 999건을 다시 넣을 이유가 없다.
     *
     * @return 실제로 넣은 행 수
     */
    @Transactional
    public int insert(List<Candidate> candidates, LocalDateTime now) {
        int inserted = 0;
        for (Candidate candidate : candidates) {
            try {
                transitLegDurationRepository.save(TransitLegDuration.requested(
                        candidate.mode(), candidate.depCode(), candidate.arrCode(), now));
                inserted++;
            } catch (DataIntegrityViolationException e) {
                log.debug("이미 등록된 구간입니다 — 넘어갑니다 {}", candidate.key());
            }
        }
        return inserted;
    }
}
