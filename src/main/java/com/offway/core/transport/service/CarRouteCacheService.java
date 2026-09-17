package com.offway.core.transport.service;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.CarLegDuration;
import com.offway.core.transport.domain.CarRouteOrder;
import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.repository.CarRouteCacheRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 자차 경로를 다시 안 묻게 기억해 둔다(#584) — 구간 소요시간과 경유지 최적 순서.
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>대중교통은 구간 소요시간을 표에 남겨 두고 쓴다(#107 · #469). <b>자차만 캐시가 통째로 없어</b>
 * 같은 코스를 다시 만들 때마다 TMAP 을 다시 불렀다. 그중 <b>경유지 최적화는 일일 한도가 50</b> 으로
 * 우리가 가진 것 중 가장 빡빡하고, 자차 코스 하나가 날짜 수만큼 부르므로 2박3일이면 17번 만에 마른다.
 *
 * <p>마르면 직선거리로 폴백하는데 <b>응답은 200 이다</b> — 순서와 소요시간이 틀린 줄 사용자가 알
 * 방법이 없다.
 *
 * <h2>이 클래스가 지키는 규칙 둘</h2>
 *
 * <p><b>① 폴백은 저장하지 않는다.</b> 직선거리 근사는 한도가 말랐거나 좌표가 도로에 안 붙을 때 나오는
 * 값이다. 그걸 캐시하면 <b>하루 한도가 마른 것이 재측정 주기 내내 굳는다</b> — 그날 이후로는 한도가
 * 멀쩡해도 계속 직선거리를 쓴다. 그래서 호출부가 "TMAP 이 실제로 답한 것" 만 넘긴다.
 *
 * <p><b>② 캐시 때문에 코스가 실패하지 않는다.</b> 읽기도 쓰기도 실패를 삼키고 넘어간다. 캐시는
 * 보조이고, 여기서 예외가 올라가면 <b>코스 생성이 통째로 죽는다</b> — 캐시가 없던 시절보다 나쁜 상태다.
 * 다만 조용히 넘기지는 않는다(로그).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarRouteCacheService {

    /** 측정 시각 기준 시간대. 재측정 주기 판정이 서버 로케일을 타지 않게 한다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CarRouteCacheRepository carRouteCacheRepository;

    /** 이 구간을 최근에 재 뒀나. */
    public Optional<Integer> legMinutes(Coordinate from, Coordinate to) {
        try {
            return carRouteCacheRepository
                    .findLeg(CoordinateKey.of(from), CoordinateKey.of(to))
                    .filter(leg -> leg.isFresh(LocalDateTime.now(SERVICE_ZONE)))
                    .map(CarLegDuration::getMinutes);
        } catch (RuntimeException e) {
            log.warn("자차 구간 캐시를 읽지 못했습니다 — TMAP 으로 갑니다 cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 잰 구간을 남긴다.
     *
     * <p><b>TMAP 이 실제로 답한 값만 넘겨야 한다.</b> 폴백을 넘기면 그 값이 재측정 주기 내내 굳는다.
     */
    public void rememberLeg(Coordinate from, Coordinate to, int minutes) {
        try {
            carRouteCacheRepository.saveLeg(CarLegDuration.measured(
                    CoordinateKey.of(from), CoordinateKey.of(to), minutes, LocalDateTime.now(SERVICE_ZONE)));
        } catch (RuntimeException e) {
            // 캐시에 못 남겼을 뿐 이번 코스는 멀쩡하다. 다음에 다시 물으면 된다.
            log.warn("자차 구간을 캐시에 남기지 못했습니다 cause={}", e.getClass().getSimpleName());
        }
    }

    /** 이 좌표 목록의 최적 순서를 최근에 받아 뒀나. */
    public Optional<List<Integer>> order(List<Coordinate> points) {
        String key = CarRouteOrder.keyOf(points);
        if (!CarRouteOrder.storable(key)) {
            return Optional.empty();
        }
        try {
            return carRouteCacheRepository
                    .findOrder(key)
                    .filter(order -> order.isFresh(LocalDateTime.now(SERVICE_ZONE)))
                    .map(CarRouteOrder::order);
        } catch (RuntimeException e) {
            log.warn("경유지 순서 캐시를 읽지 못했습니다 — TMAP 으로 갑니다 cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 받은 순서를 남긴다.
     *
     * <p>키가 칸을 넘치면 <b>저장을 건너뛴다</b> — 없는 것처럼 굴 뿐 코스는 그대로 나간다. 점 개수가
     * 3~12 로 고정돼 있어 실제로는 안 넘치지만, 넘쳤을 때 예외로 코스를 죽이지는 않는다.
     */
    public void rememberOrder(List<Coordinate> points, List<Integer> order) {
        String key = CarRouteOrder.keyOf(points);
        if (!CarRouteOrder.storable(key)) {
            log.warn("경유지 좌표 목록이 캐시 칸을 넘칩니다 — 캐시하지 않습니다 길이={}", key.length());
            return;
        }
        try {
            carRouteCacheRepository.saveOrder(
                    CarRouteOrder.measured(points, order, LocalDateTime.now(SERVICE_ZONE)));
        } catch (RuntimeException e) {
            log.warn("경유지 순서를 캐시에 남기지 못했습니다 cause={}", e.getClass().getSimpleName());
        }
    }
}
