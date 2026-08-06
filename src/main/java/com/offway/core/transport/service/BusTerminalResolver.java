package com.offway.core.transport.service;

import com.offway.core.transport.domain.BusTerminal;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.repository.BusTerminalRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 좌표를 고속버스 터미널로 해석한다(#107) — 버스 접근의 관문. 전국 터미널 마스터(시드 452곳)를 인메모리로 들고
 * <b>좌표 최근접</b>으로 찾는다. {@link TrainStationResolver} 와 같은 모양이다.
 *
 * <p><b>상한 거리가 열차보다 짧다.</b> 역은 {@code 50㎞} 를 쓰는데, 터미널은 역보다 촘촘해 그만큼 멀리 잡을 이유가
 * 없다. 반대로 너무 멀리 잡으면 "버스로 갈 수 있다" 고 안내해놓고 정작 터미널까지 한 시간을 더 가야 한다.
 *
 * <p>좌표가 없는 터미널은 탐색에서 빠진다 — 목록에 실제 터미널이 아닌 항목이 섞여 있어 지오코딩이 전부 되지는 않는다.
 */
@Service
@RequiredArgsConstructor
public class BusTerminalResolver {

    /** 이 반경 안에 터미널이 없으면 "버스 접근 불가"로 본다. */
    private static final double MAX_KM = 30.0;

    private final BusTerminalRepository terminalRepository;
    private volatile List<BusTerminal> cache;

    /** 좌표에서 {@value #MAX_KM}㎞ 안 가장 가까운 터미널. 없으면 빈 Optional. */
    public Optional<Terminal> nearest(double lat, double lng) {
        Coordinate target = new Coordinate(lat, lng);
        return terminals().stream()
                .filter(BusTerminal::hasCoordinate)
                .map(t -> Map.entry(t, target.haversineKmTo(new Coordinate(t.getLat(), t.getLng()))))
                .filter(entry -> entry.getValue() <= MAX_KM)
                .min(Comparator.comparingDouble(Map.Entry::getValue))
                .map(entry -> new Terminal(
                        entry.getKey().getCode(),
                        entry.getKey().getName(),
                        entry.getKey().getKind(),
                        new Coordinate(entry.getKey().getLat(), entry.getKey().getLng())));
    }

    /** 캐시 무효화 — 시드 갱신·통합 테스트 격리용. */
    public void evictCache() {
        cache = null;
    }

    private List<BusTerminal> terminals() {
        List<BusTerminal> local = cache;
        if (local == null) {
            local = terminalRepository.findAll();
            cache = local;
        }
        return local;
    }
}
