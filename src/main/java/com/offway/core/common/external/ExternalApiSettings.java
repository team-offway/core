package com.offway.core.common.external;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지금 켜져 있는 연동 설정(#403) — <b>읽는 쪽이 매번 DB 를 치지 않게</b> 메모리에 든다.
 *
 * <p>캐시 사용 여부는 <b>요청마다</b> 물어보는 값이라(장소 상세 한 번에도 여러 번) DB 를 치면 그 자체가
 * 부담이다. 반대로 값이 바뀌었는데 오래 안 반영되면 스위치가 있으나 마나다.
 *
 * <p>그래서 둘 다 한다 — <b>쓰는 즉시 갱신</b>하고, {@value #REFRESH_INTERVAL} 마다 한 번 더 읽는다.
 * 앞쪽이 이 인스턴스를 맞추고, 뒤쪽은 인스턴스가 늘었을 때의 안전망이다(지금은 한 대뿐이라 앞쪽이면
 * 충분하지만, 늘어난 날 조용히 어긋나는 것보다 낫다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalApiSettings implements ExternalApiCachePolicy, ExternalApiBatchPolicy {

    /** 다른 인스턴스가 바꾼 값을 늦어도 이만큼 안에 본다. */
    private static final String REFRESH_INTERVAL = "PT60S";

    private final ExternalApiSettingRepository repository;
    private final ExternalApiCallRecorder callRecorder;

    private volatile Map<ExternalApi, ExternalApiSetting> apiSettings = Map.of();
    private volatile Set<String> disabledBatches = Set.of();

    /**
     * 주기 갱신 - <b>실패해도 직전 값을 유지한다.</b>
     *
     * <p>읽기 실패로 기본값으로 되돌아가지 않는다. 꺼 둔 배치가 조용히 다시 도는 것이 더 나쁘다.
     *
     * <p>변경 경로는 이 메서드를 쓰지 않는다({@link #reload} 를 직접 부른다) - 여기서 삼킨 실패를
     * 저장 성공으로 돌려주면, 어드민은 200 을 받고도 최대 {@value #REFRESH_INTERVAL} 동안 옛 정책이
     * 도는 것을 보게 된다.
     */
    @PostConstruct
    @Scheduled(fixedDelayString = REFRESH_INTERVAL)
    public void refresh() {
        try {
            reload();
        } catch (RuntimeException e) {
            log.warn("연동 설정을 다시 읽지 못했습니다. 직전 값을 유지합니다 cause={}", e.getClass().getSimpleName());
        }
    }

    /** 실제로 읽어 담는다 - 실패를 <b>삼키지 않는다</b>. 삼킬지는 부르는 쪽이 정한다. */
    private void reload() {
        apiSettings = repository.findApiSettings();
        disabledBatches = repository.findBatchSettings().entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 손대지 않은 연동은 기본값이다 — 이 기능이 붙기 전과 같은 동작. */
    public ExternalApiSetting of(ExternalApi api) {
        return apiSettings.getOrDefault(api, ExternalApiSetting.defaultFor(api));
    }

    /**
     * 이 연동의 인메모리 캐시를 쓸지.
     *
     * <p>끄면 <b>매번 실호출</b>한다. 값이 항상 최신이 되는 대신 한도를 그만큼 더 태운다 — 그래서
     * 화면이 바꾸기 전에 예상 콜 수를 알려준다.
     */
    @Override
    public boolean cacheEnabled(ExternalApi api) {
        return of(api).cacheEnabled();
    }

    /** 이 배치가 돌아도 되나. 설정이 없으면 돈다. */
    public boolean batchEnabled(String batchName) {
        return !disabledBatches.contains(batchName);
    }

    /**
     * 이 배치가 지금 이 연동을 불러도 되나 — <b>배치가 사용자 몫을 먹지 않게</b>.
     *
     * <p>스위치와 상한을 한 자리에서 묻게 한 것은, 배치마다 둘을 따로 확인하면 한쪽을 빠뜨리기
     * 때문이다. 부르는 쪽은 "돌아도 되나" 한 번만 물으면 된다.
     *
     * <p><b>이건 호출 하나를 예약하는 것이 아니라 실행 하나를 여는 관문이다.</b> 배치는 이 질문을
     * 시작할 때 한 번만 하고 그 뒤로 수백 건을 쏘므로, 상한은 정확히 지켜지는 쿼터가 아니라
     * "이 시점에 이미 넘었으면 이번 실행은 건너뛴다" 는 뜻이다. 초과분의 상한은 배치 한 번이
     * 쓰는 양이고, 그건 의도한 값이다 — 실행 중간에 끊으면 절반만 적재된 상태가 남는다.
     */
    @Override
    public boolean batchMayCall(String batchName, ExternalApi api) {
        if (!batchEnabled(batchName)) {
            return false;
        }
        return of(api).allowsBatch(usedToday(api));
    }

    /**
     * 연동 설정을 바꾸고 <b>그 자리에서 반영</b>한다(#403).
     *
     * <p>저장만 하고 주기 갱신을 기다리면, 어드민은 스위치를 내린 뒤에도 화면에서 옛 값을 본다 —
     * 그러면 한 번 더 누르게 되고 그게 또 안 듣는 것처럼 보인다.
     *
     * <p><b>반영에 실패하면 저장도 되돌린다.</b> 주기 갱신과 달리 여기서는 실패를 삼키지 않는다 -
     * 삼키면 행은 바뀌었는데 이 인스턴스는 옛 정책으로 도는 상태가 성공 응답으로 나가, 어드민은
     * 스위치가 걸린 줄 알고 최대 {@value #REFRESH_INTERVAL} 을 기다린다. 트랜잭션으로 묶어
     * DB 와 메모리가 같은 결말을 맞게 한다.
     */
    @Transactional
    public ExternalApiSetting update(ExternalApi api, boolean cacheEnabled, Integer batchLimit, String updatedBy) {
        ExternalApiSetting setting = new ExternalApiSetting(api, cacheEnabled, batchLimit);
        repository.save(setting, updatedBy);
        reload();
        return setting;
    }

    /** 배치를 멈추거나 다시 돌린다. 같은 이유로 그 자리에서 반영한다. */
    @Transactional
    public void updateBatch(String batchName, boolean enabled, String updatedBy) {
        repository.saveBatch(batchName, enabled, updatedBy);
        reload();
    }

    /** 지금 손댄 연동 전부 — 화면이 "기본에서 벗어난 것" 을 짚어줄 수 있게. */
    public Map<ExternalApi, ExternalApiSetting> touched() {
        return apiSettings;
    }

    /** 지금 꺼 둔 배치 이름. */
    public Set<String> disabledBatches() {
        return disabledBatches;
    }

    private long usedToday(ExternalApi api) {
        return callRecorder.snapshotToday().totals().getOrDefault(api, 0L);
    }
}
