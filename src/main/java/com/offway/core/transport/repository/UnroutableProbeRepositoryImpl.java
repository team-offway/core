package com.offway.core.transport.repository;

import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.domain.UnroutableProbe;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UnroutableProbeRepositoryImpl implements UnroutableProbeRepository {

    private final UnroutableProbeJpaRepository unroutableProbeJpaRepository;

    @Override
    public void saveIfAbsent(UnroutableProbe probe) {
        if (unroutableProbeJpaRepository.existsByLatAndLngAndPartnerLatAndPartnerLng(
                probe.getLat(), probe.getLng(), probe.getPartnerLat(), probe.getPartnerLng())) {
            return;
        }
        try {
            unroutableProbeJpaRepository.save(probe);
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 다른 요청이 같은 구간을 넣었다. 원하던 상태가 이미 됐으므로 성공이다 —
            // 여기서 예외를 올리면 이동시간 기록이 코스 생성을 통째로 실패시킨다.
            log.debug("이미 기록된 구간입니다 — 무시합니다");
        }
    }

    @Override
    public Set<CoordinateKey> pointsWithAtLeast(int minPartners) {
        Set<CoordinateKey> points = new LinkedHashSet<>();
        for (Object[] row : unroutableProbeJpaRepository.findPointsWithAtLeast(minPartners)) {
            points.add(new CoordinateKey((BigDecimal) row[0], (BigDecimal) row[1]));
        }
        return points;
    }
}
