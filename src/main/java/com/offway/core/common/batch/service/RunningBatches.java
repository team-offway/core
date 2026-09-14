package com.offway.core.common.batch.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지금 도는 배치 — <b>스케줄러와 수동 실행이 같은 표식을 잡는다</b>(#540).
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>예전에는 이 표식이 {@link ManualBatchService} 안에만 있어 <b>수동 실행끼리만</b> 막았다. 스케줄러는
 * 그 서비스를 안 거치고 각 배치의 {@code @Scheduled} 메서드를 직접 부르므로, 스케줄러가 도는 중에
 * 관리자가 같은 배치를 누르면 <b>둘이 함께 돌았다</b> — 외부 호출을 두 배로 태우고, 교체 중인 데이터를
 * 서로 밟는다.
 *
 * <h2>간격 가드와 다른 축이다</h2>
 *
 * <p>{@code tryStartSince}·{@code tryStartOn} 은 "<b>또 돌 때가 됐나</b>" 를 DB 로 가른다. 여기는
 * "<b>지금 도는 중인가</b>" 다. 둘은 독립이라 간격 가드가 있는 배치도 이 표식이 필요하다 — 가드를
 * 통과한 두 실행이 동시에 들어오면 둘 다 통과한다.
 *
 * <h2>왜 DB 가 아니라 프로세스 안인가</h2>
 *
 * <p>배포가 <b>옛 컨테이너를 먼저 지우고</b> 새것을 띄운다({@code docker rm -f offway-core} 다음에
 * {@code docker run}). 두 인스턴스가 겹치는 구간이 없으므로 프로세스 안 표식으로 충분하다.
 *
 * <p>인스턴스를 늘리게 되면 이 판단이 깨진다. 그때는 여기만 DB 조건부 UPDATE 로 바꾸면 되고, 부르는
 * 쪽 열다섯 군데는 그대로다 — 그러라고 이 자리를 뗐다.
 */
@Slf4j
@Component
public class RunningBatches {

    /** 도는 배치 이름 → 시작 시각. 시각을 들면 로그가 "얼마나 돌고 있나" 를 말할 수 있다. */
    private final Map<String, Instant> running = new ConcurrentHashMap<>();

    /**
     * 선점하고 돌린다 — 이미 도는 중이면 <b>아무것도 하지 않는다</b>.
     *
     * <p>선점과 확인이 {@code putIfAbsent} 한 문장이라 그 사이에 다른 실행이 끼어들 수 없다.
     *
     * <p><b>예외를 삼키지 않는다.</b> 배치 안에서 난 예외는 그대로 올라간다 — 수동 실행이라면 화면이
     * 실패를 봐야 하고, 스케줄이라면 스프링이 로그를 남긴다. 표식만 {@code finally} 로 푼다.
     *
     * @param name 배치 이름({@code batch_run.name} 과 같은 값)
     * @param task 실제로 돌 일
     * @return 진입했으면 참. <b>거짓이면 같은 배치가 이미 도는 중이라 이번 호출은 아무 일도 안 했다</b> —
     *     "돌았는데 간격 가드가 건너뛰었다" 와는 다르다. 그건 참이다
     */
    public boolean runExclusively(String name, Runnable task) {
        Instant startedAt = Instant.now();
        Instant already = running.putIfAbsent(name, startedAt);
        if (already != null) {
            // 조용히 넘기지 않는다 — 겹쳐서 안 돈 것과 할 일이 없어 안 돈 것은 다른 사건이다.
            log.info("같은 배치가 이미 도는 중이라 건너뜁니다 name={} 경과={}초",
                    name, Duration.between(already, startedAt).toSeconds());
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            running.remove(name);
        }
    }
}
