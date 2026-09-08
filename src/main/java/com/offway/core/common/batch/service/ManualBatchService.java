package com.offway.core.common.batch.service;

import com.offway.core.common.batch.domain.BatchException;
import com.offway.core.common.batch.domain.ManualBatch;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 배치를 손으로 돌린다(#537).
 *
 * <h2>왜 이 자리인가</h2>
 *
 * <p>배치를 고쳐도 다음 주기까지 결과를 못 본다 — 주 1회짜리는 엿새다. 그동안 고친 것이 실제로 도는지
 * 알 방법이 없고, 로그는 컨테이너가 바뀌면 사라진다(#535 가 그렇게 4주를 갔다).
 *
 * <h2>같은 배치를 겹쳐 돌리지 않는다</h2>
 *
 * <p>배치는 대개 외부를 부르고 DB 를 통째로 갈아 끼운다. 두 번 눌러 <b>같은 배치가 겹쳐 돌면</b> 한도를
 * 두 배로 태우고, 교체 중인 데이터를 서로 밟는다. 그래서 도는 동안은 같은 이름을 거절한다.
 *
 * <h2>기다렸다가 결과를 돌려준다</h2>
 *
 * <p>비동기로 던지고 "시작했습니다" 만 답하면 <b>무엇이 일어났는지를 다시 로그에서 찾아야 한다</b> —
 * 그게 이 기능을 만든 이유와 정면으로 어긋난다. 관리자 호출이라 몇 초·몇 분 기다려도 되고, 대신
 * 실패는 실패로 돌려준다.
 */
@Slf4j
@Service
public class ManualBatchService {

    private final Map<String, ManualBatch> batches;

    /** 지금 도는 배치 — 이름만 담는다. 값은 시작 시각이라 로그에 "얼마나 돌고 있나" 를 적을 수 있다. */
    private final Map<String, Instant> running = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 등록된 배치를 이름으로 모은다.
     *
     * <p><b>이름이 겹치면 부팅에서 끊는다.</b> 겹친 채로 두면 관리자가 A 를 눌렀는데 B 가 도는데,
     * 그건 화면만 봐서는 알 수 없다.
     */
    public ManualBatchService(List<ManualBatch> registered) {
        Map<String, ManualBatch> byName = new LinkedHashMap<>();
        for (ManualBatch batch : registered) {
            ManualBatch previous = byName.put(batch.batchName(), batch);
            if (previous != null) {
                throw new IllegalStateException("배치 이름이 겹칩니다: " + batch.batchName());
            }
        }
        this.batches = Map.copyOf(byName);
        log.info("손으로 돌릴 수 있는 배치 {}개: {}", batches.size(), batches.keySet());
    }

    /** 손으로 돌릴 수 있는 배치 이름 — 관리자 목록이 마지막 실행 시각을 여기에 붙인다. */
    public List<String> names() {
        return List.copyOf(batches.keySet());
    }

    /**
     * 지금 돌린다.
     *
     * @return 걸린 시간
     * @throws BatchException 없는 이름이거나 이미 도는 중일 때
     */
    public Duration run(String name) {
        ManualBatch batch = batches.get(name);
        if (batch == null) {
            throw BatchException.unknownBatch();
        }
        if (running.putIfAbsent(name, Instant.now()) != null) {
            throw BatchException.alreadyRunning();
        }
        Instant startedAt = Instant.now();
        try {
            log.info("배치를 손으로 돌립니다 name={}", name);
            batch.runNow();
            Duration took = Duration.between(startedAt, Instant.now());
            log.info("배치 수동 실행 완료 name={} 걸린시간={}초", name, took.toSeconds());
            return took;
        } catch (RuntimeException e) {
            // 배치 안에서 난 예외를 삼키지 않는다 — 손으로 부른 이유가 "무엇이 잘못됐나" 이기 때문이다.
            log.warn("배치 수동 실행 실패 name={} cause={}", name, e.getClass().getSimpleName(), e);
            throw BatchException.runFailed();
        } finally {
            running.remove(name);
        }
    }
}
