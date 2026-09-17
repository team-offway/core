package com.offway.core.transport.infrastructure;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.service.CarRouteCacheService;
import com.offway.core.transport.service.RouteOptimizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 방문 순서 최적화 어댑터 — TMAP 경유지 최적화(실도로)를 우선 쓰고, 키 없음·범위 밖·실패면 직선거리 최근접으로 폴백한다.
 */
@Component
@RequiredArgsConstructor
public class TmapRouteOptimizer implements RouteOptimizer {

    private final TmapClient tmapClient;
    private final CarRouteCacheService carRouteCacheService;

    /**
     * <b>최근에 받아 둔 순서면 다시 묻지 않는다</b>(#584).
     *
     * <p>이 한 자리가 이 캐시를 만드는 가장 큰 이유다. 경유지 최적화는 <b>일일 한도가 50</b> 으로
     * 우리가 가진 것 중 가장 빡빡하고, 자차 코스 하나가 날짜 수만큼 부르므로 2박3일이면 <b>17번 만에
     * 마른다.</b> 마르면 아래 직선거리 폴백으로 떨어지는데 응답은 200 이라, 순서가 틀린 줄 사용자가
     * 알 방법이 없다.
     *
     * <p><b>폴백은 캐시에 안 남긴다.</b> 남기면 한도가 마른 하루가 재측정 주기 내내 굳는다.
     */
    @Override
    public List<Integer> optimalOrder(List<Coordinate> points) {
        if (points.size() <= 2) {
            return identity(points.size()); // 0~2곳은 순서 최적화가 의미 없다
        }
        Optional<List<Integer>> cached = carRouteCacheService.order(points);
        if (cached.isPresent()) {
            return cached.get();
        }
        Optional<List<Integer>> measured = tmapClient.optimizeCarOrder(points);
        measured.ifPresent(order -> carRouteCacheService.rememberOrder(points, order));
        return measured.orElseGet(() -> nearestNeighbor(points));
    }

    /** 첫 지점에서 직선거리로 가장 가까운 곳을 이어붙이는 폴백(그리디). */
    private List<Integer> nearestNeighbor(List<Coordinate> points) {
        List<Integer> order = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        int current = 0;
        order.add(current);
        used.add(current);
        while (order.size() < points.size()) {
            int next = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < points.size(); i++) {
                if (used.contains(i)) {
                    continue;
                }
                double distance = points.get(current).haversineKmTo(points.get(i));
                if (distance < best) {
                    best = distance;
                    next = i;
                }
            }
            order.add(next);
            used.add(next);
            current = next;
        }
        return order;
    }

    private List<Integer> identity(int size) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        return order;
    }
}
