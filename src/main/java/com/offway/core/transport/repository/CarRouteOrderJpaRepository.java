package com.offway.core.transport.repository;

import com.offway.core.transport.domain.CarRouteOrder;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data — {@link CarRouteCacheRepositoryImpl} 이 위임한다. */
interface CarRouteOrderJpaRepository extends JpaRepository<CarRouteOrder, Long> {

    Optional<CarRouteOrder> findByPoints(String points);

    /**
     * 처음 재면 넣고, 다시 재면 갈아 끼운다 — {@code CarLegDurationJpaRepository.upsert} 와 같은 이유다.
     * 가르는 기준이 유니크 키({@code points})라 {@code save} 로는 재측정에서 제약 위반이 난다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO car_route_order (points, ordinals, measured_at)
                    VALUES (:points, :ordinals, :measuredAt) AS incoming
                    ON DUPLICATE KEY UPDATE
                        ordinals = incoming.ordinals,
                        measured_at = incoming.measured_at
                    """,
            nativeQuery = true)
    void upsert(
            @Param("points") String points,
            @Param("ordinals") String ordinals,
            @Param("measuredAt") LocalDateTime measuredAt);
}
