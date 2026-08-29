package com.offway.core.transport.repository;

import com.offway.core.transport.domain.UnroutableProbe;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface UnroutableProbeJpaRepository extends JpaRepository<UnroutableProbe, Long> {

    boolean existsByLatAndLngAndPartnerLatAndPartnerLng(
            BigDecimal lat, BigDecimal lng, BigDecimal partnerLat, BigDecimal partnerLng);

    /**
     * 짝이 {@code minPartners} 가지 이상인 좌표 — 차단 대상.
     *
     * <p>{@code COUNT(*)} 로 세도 되는 이유는 같은 구간이 UNIQUE 로 한 줄이기 때문이다. 행 하나가 곧
     * 서로 다른 짝 하나다.
     */
    @Query("""
            select p.lat, p.lng from UnroutableProbe p
            group by p.lat, p.lng
            having count(p) >= :minPartners
            """)
    List<Object[]> findPointsWithAtLeast(int minPartners);
}
