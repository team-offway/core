package com.offway.core.transport.repository;

import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.domain.UnroutableProbe;
import java.util.Set;

/** 도메인이 의존하는 port. 구현은 {@link UnroutableProbeRepositoryImpl}. */
public interface UnroutableProbeRepository {

    /**
     * 이미 있는 구간이면 아무것도 하지 않는다.
     *
     * <p>같은 구간의 재실패를 또 적으면 <b>한 코스를 반복 생성하는 것만으로 차단 조건이 채워진다.</b>
     * 증거는 "서로 다른 짝" 이어야 뜻이 있다.
     */
    void saveIfAbsent(UnroutableProbe probe);

    /** 짝이 {@code minPartners} 가지 이상 쌓인 좌표. */
    Set<CoordinateKey> pointsWithAtLeast(int minPartners);
}
