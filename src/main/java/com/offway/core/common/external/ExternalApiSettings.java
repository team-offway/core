package com.offway.core.common.external;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    @PostConstruct
    @Scheduled(fixedDelayString = REFRESH_INTERVAL)
    public void refresh() {
        try {
            apiSettings = repository.findApiSettings();
            disabledBatches = repository.findBatchSettings().entrySet().stream()
                    .filter(entry -> !entry.getValue())
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            // 읽기 실패로 기본값으로 되돌아가지 않는다 — 꺼 둔 배치가 조용히 다시 도는 것이 더 나쁘다.
            log.warn("연동 설정을 다시 읽지 못했습니다. 직전 값을 유지합니다 cause={}", e.getClass().getSimpleName());
        }
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
     */
    public ExternalApiSetting update(ExternalApi api, boolean cacheEnabled, Integer batchLimit, String updatedBy) {
        ExternalApiSetting setting = new ExternalApiSetting(api, cacheEnabled, batchLimit);
        repository.save(setting, updatedBy);
        refresh();
        return setting;
    }

    /** 배치를 멈추거나 다시 돌린다. 같은 이유로 그 자리에서 반영한다. */
    public void updateBatch(String batchName, boolean enabled, String updatedBy) {
        repository.saveBatch(batchName, enabled, updatedBy);
        refresh();
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
