package com.offway.core.transport.service;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.FerryPort;
import com.offway.core.transport.domain.Port;
import com.offway.core.transport.repository.FerryPortRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 좌표를 여객선 항구로 해석한다(#97) — 배 접근의 관문. 전국 항구 마스터(시드 500곳)를 인메모리로 들고 <b>좌표
 * 최근접</b>으로 찾는다. {@link TrainStationResolver}·{@link BusTerminalResolver} 와 같은 모양이다.
 *
 * <p><b>상한 거리가 셋 중 가장 짧다.</b> 항구는 섬·해안에 몰려 있어 조금만 넓게 잡아도 바다 건너 항구가 잡힌다.
 * 육지 한복판 좌표에 "배로 갈 수 있다" 고 답하면 안 되므로 좁게 둔다.
 *
 * <table>
 *   <caption>수단별 상한</caption>
 *   <tr><th>수단</th><th>상한</th><th>이유</th></tr>
 *   <tr><td>기차역</td><td>50㎞</td><td>역이 드물어 넓게 봐야 한다</td></tr>
 *   <tr><td>버스터미널</td><td>30㎞</td><td>역보다 촘촘하다</td></tr>
 *   <tr><td>여객선 항구</td><td>{@value #MAX_KM}㎞</td><td>바다 건너를 잡지 않게</td></tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
public class FerryPortResolver {

    /** 이 반경 안에 항구가 없으면 "배로 갈 수 없다"고 본다. */
    private static final double MAX_KM = 20.0;

    private final FerryPortRepository portRepository;
    private volatile List<FerryPort> cache;

    /** 좌표에서 {@value #MAX_KM}㎞ 안 가장 가까운 항구. 없으면 빈 Optional. */
    public Optional<Port> nearest(double lat, double lng) {
        Coordinate target = new Coordinate(lat, lng);
        return ports().stream()
                .filter(FerryPort::hasCoordinate)
                .map(p -> Map.entry(p, target.haversineKmTo(new Coordinate(p.getLat(), p.getLng()))))
                .filter(entry -> entry.getValue() <= MAX_KM)
                .min(Comparator.comparingDouble(Map.Entry::getValue))
                .map(entry -> new Port(
                        entry.getKey().getCode(),
                        entry.getKey().getName(),
                        new Coordinate(entry.getKey().getLat(), entry.getKey().getLng())));
    }

    /** 캐시 무효화 — 시드 갱신·통합 테스트 격리용. */
    public void evictCache() {
        cache = null;
    }

    private List<FerryPort> ports() {
        List<FerryPort> local = cache;
        if (local == null) {
            local = portRepository.findAll();
            cache = local;
        }
        return local;
    }
}
