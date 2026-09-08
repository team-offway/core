package com.offway.core.transport.service;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.domain.UnroutableProbe;
import com.offway.core.transport.domain.UnroutableReason;
import com.offway.core.transport.repository.UnroutableProbeRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TMAP 이 못 푸는 좌표를 기억하고, 다음 코스에서 빼도록 알려준다(#335).
 *
 * <p>transport 가 소유한다 — "이 좌표로 경로를 만들 수 있는가" 는 교통의 질문이고, 답을 아는 것은 TMAP
 * 어댑터뿐이다. 코스(itinerary)는 {@link #blockedPoints()} 만 물어본다.
 *
 * <h2>사전 검사를 하지 않는 이유</h2>
 *
 * <p>장소마다 미리 TMAP 을 불러 확인하는 방식은 한도가 감당 못 한다 — 경로 API 는 일 1,000건이고 장소 풀은
 * 12만 건이다. 그래서 <b>쓰다가 걸린 것만</b> 기억한다. 초기 적재가 필요 없고, 실제로 코스에 들어가는
 * 좌표만 쌓이므로 자연히 필요한 만큼만 찬다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnroutableCoordinateService {

    /**
     * 차단에 필요한 <b>서로 다른 짝</b>의 수.
     *
     * <p>둘인 이유: 진짜 못 푸는 좌표는 들어오는 구간과 나가는 구간이 둘 다 실패해 짝이 둘 이상 쌓이고,
     * 그 옆에 있었을 뿐인 멀쩡한 좌표는 짝이 하나뿐이다. 하나로 낮추면 나쁜 좌표의 <b>이웃</b>까지 함께
     * 차단된다 — 코스에서 조용히 사라지는 쪽은 언제나 멀쩡한 장소가 더 많다.
     *
     * <p>대신 하루의 첫·마지막 슬롯처럼 구간이 하나뿐인 자리에서 걸린 좌표는 그 코스만으로는 안 잡힌다.
     * 다음 코스에서 다른 짝과 만나 채워진다.
     */
    private static final int MIN_DISTINCT_PARTNERS = 2;

    private final UnroutableProbeRepository unroutableProbeRepository;

    /**
     * 거절된 구간을 <b>양쪽 다</b> 기록한다 — 어느 쪽이 나쁜지 아직 모르기 때문이다.
     *
     * <p>기록은 코스 생성의 곁가지다. 여기서 예외가 올라가면 이동시간 한 건을 못 적은 일이 코스 응답
     * 전체를 실패시킨다. 그래서 삼켜서 warn 으로 남긴다.
     */
    @Transactional
    public void report(Coordinate from, Coordinate to, UnroutableReason reason) {
        CoordinateKey origin = CoordinateKey.of(from);
        CoordinateKey destination = CoordinateKey.of(to);
        try {
            unroutableProbeRepository.saveIfAbsent(probe(origin, destination, reason));
            unroutableProbeRepository.saveIfAbsent(probe(destination, origin, reason));
        } catch (RuntimeException e) {
            log.warn("경로 불가 좌표 기록 실패 — 코스는 그대로 진행한다 reason={}", reason, e);
        }
    }

    /**
     * 앞으로 코스에 넣지 않을 좌표.
     *
     * <p>테이블이 작고(하루 열 행 남짓) 인덱스 조회라 코스 생성마다 읽어도 된다. <b>캐시를 두지 않는
     * 이유</b>는 방금 걸린 좌표가 곧바로 다음 코스에서 빠져야 하기 때문이다 — 캐시가 그 앞을 막으면
     * 같은 잘못된 코스를 TTL 만큼 더 만든다.
     */
    @Transactional(readOnly = true)
    public Set<CoordinateKey> blockedPoints() {
        return unroutableProbeRepository.pointsWithAtLeast(MIN_DISTINCT_PARTNERS);
    }

    private static UnroutableProbe probe(CoordinateKey point, CoordinateKey partner, UnroutableReason reason) {
        return UnroutableProbe.builder().point(point).partner(partner).reason(reason).build();
    }
}
