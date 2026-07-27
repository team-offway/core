package com.offway.core.transport.infrastructure;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.service.RouteOptimizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Override
    public List<Integer> optimalOrder(List<Coordinate> points) {
        if (points.size() <= 2) {
            return identity(points.size()); // 0~2곳은 순서 최적화가 의미 없다
        }
        return tmapClient.optimizeCarOrder(points).orElseGet(() -> nearestNeighbor(points));
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
