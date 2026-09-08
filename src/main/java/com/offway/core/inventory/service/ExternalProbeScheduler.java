package com.offway.core.inventory.service;

import com.offway.core.common.external.ExternalApiHealth;
import com.offway.core.inventory.infrastructure.probe.ExternalApiProbe;
import com.offway.core.inventory.infrastructure.probe.ProbeResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 트래픽이 없는 시간대의 관측 공백을 메운다(#474).
 *
 * <p><b>왜 필요한가.</b> 상태 판정은 {@code ExternalApiHealth} 가 <b>이미 하고 있는 호출의 결과</b>로
 * 한다. 그게 공짜이고 사용자가 실제로 겪는 것과 같기 때문이다. 다만 그 방식은 <b>아무도 안 쓰는 새벽에는
 * 아무것도 모른다</b> — 6시에 죽은 게이트웨이를 9시 첫 사용자가 알려주게 된다.
 *
 * <p>그래서 주기적으로 한 번씩 쏜다. 새로 만들지 않고 <b>어드민 인벤토리 페이지가 쓰는 프로브를 그대로
 * 돌린다</b> — URL·키·성공 판정이 이미 그 안에 있어서, 여기서 다시 적으면 두 곳이 어긋난다.
 *
 * <p>프로브도 {@code externalWebClient} 를 타므로 <b>성패는 필터가 알아서 기록한다.</b> 이 클래스는
 * 호출을 일으키는 것 외에 상태를 직접 쓰지 않는다 — 기록자가 둘이면 한쪽이 다른 쪽을 덮는다.
 *
 * <h2>죽은 것 같으면 그 자리에서 두 번 더 묻는다</h2>
 *
 * <p>연속 3회 실패라야 장애로 보는데, 주기가 30분이면 확정까지 1시간 반이 걸린다. 그래서 실패한 프로브만
 * 곧바로 두 번 더 부른다 — 정상일 땐 추가 비용이 0 이고, 죽었을 땐 한 주기 안에 확정된다.
 *
 * <h2>한도</h2>
 *
 * <p>평시에는 프로브 하나가 하루 48회다(30분 주기). 가장 빡빡한 관광정보(1,000)의 <b>5%</b>다.
 *
 * <p><b>장애가 이어지면 그 값이 세 배가 된다.</b> 실패한 프로브만 두 번 더 부르므로 주기당 3회, 하루
 * <b>144회 · 14.4%</b> 가 상한이다. 그래도 두는 이유는 그 상황에서 태우는 한도가 <b>이미 못 쓰는
 * API</b> 의 것이기 때문이다. 다만 data.go.kr 은 여러 API 가 키 하나를 공유하므로, 한 곳의 장애가
 * 나머지 몫까지 갉을 수 있다 — 그래서 4xx(우리 요청 문제)는 확인 호출에서 빼 상한을 낮춘다.
 *
 * <p>이 계산이 주기를 30분으로 정한 근거고, 더 촘촘하게 하려면 두 숫자를 다시 재야 한다.
 *
 * <h2>기본은 꺼져 있다</h2>
 *
 * <p>운영에서만 켠다. 로컬·테스트에서 켜지면 통합 테스트가 실제 외부를 때린다 — 프로브는 port 를 거치지
 * 않아 stub 으로 못 막는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "offway.external-probe.enabled", havingValue = "true")
public class ExternalProbeScheduler {

    private static final long INTERVAL_MS = 30 * 60 * 1000L;

    /** 부팅 직후는 건너뛴다 — 시딩·워밍과 겹치면 그쪽이 느려진다. */
    private static final long INITIAL_DELAY_MS = 5 * 60 * 1000L;

    /** 실패했을 때 그 자리에서 더 물어보는 횟수. 장애 확정선(연속 3회)에 한 주기 안에 닿게 한다. */
    private static final int CONFIRMATIONS = 2;

    private final List<ExternalApiProbe> probes;
    private final ExternalApiHealth health;

    public ExternalProbeScheduler(List<ExternalApiProbe> probes, ExternalApiHealth health) {
        this.probes = probes;
        this.health = health;
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void probeAll() {
        for (ExternalApiProbe probe : probes) {
            confirm(probe);
        }
    }

    private void confirm(ExternalApiProbe probe) {
        ProbeResult result = report(probe, probe.probe());
        if (!result.unusable()) {
            return;
        }
        log.warn("외부 프로브 실패 — {} status={} http={} detail={}",
                result.name(), result.status(), result.httpStatus(), result.detail());
        if (!result.worthConfirming()) {
            // 4xx — 우리 요청이 잘못된 것이라 더 물어도 답이 같다. 한도만 태운다.
            return;
        }
        for (int i = 0; i < CONFIRMATIONS; i++) {
            report(probe, probe.probe());
        }
    }

    /**
     * 프로브 결과를 상태 판정으로 옮긴다(#479) — <b>여기가 이 경로의 유일한 기록자다</b>.
     *
     * <p>프로브 요청은 {@code ExternalHealthFilter} 가 비켜서므로(skip 마커), 200 안에 실린
     * {@code resultCode} 실패가 여기서 처음으로 실패로 세어진다. 그게 이 작업의 요점이다 — 키가
     * 만료됐거나 한도를 태운 날, 지금까지는 <b>"전부 정상" 을 보고 있었다.</b>
     *
     * <p>확인 호출의 결과도 같은 경로로 흘린다. 그래야 한 주기 안에 연속 3회가 쌓여 장애가 확정된다.
     */
    private ProbeResult report(ExternalApiProbe probe, ProbeResult result) {
        if (!result.observed()) {
            return result; // 키가 없거나 못 재본 것 — 외부에 대해 아무것도 말해주지 않는다
        }
        if (result.unusable()) {
            health.failed(probe.system(), "%s(HTTP %d)".formatted(result.detail(), result.httpStatus()));
        } else {
            health.succeeded(probe.system());
        }
        return result;
    }
}
