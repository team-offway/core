package com.offway.core.transport.repository;

import com.offway.core.transport.domain.CarLegDuration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data — {@link CarRouteCacheRepositoryImpl} 이 위임한다. */
interface CarLegDurationJpaRepository extends JpaRepository<CarLegDuration, Long> {

    Optional<CarLegDuration> findByFromLatAndFromLngAndToLatAndToLng(
            BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng);

    /**
     * 처음 재면 넣고, 다시 재면 갈아 끼운다 — 한 문장으로.
     *
     * <p><b>{@code save} 로는 안 된다.</b> 그것은 식별자로만 신규·기존을 가르는데, 여기서 같은 것을
     * 가르는 기준은 유니크 키 {@code (from_lat, from_lng, to_lat, to_lng)} 다. 재측정 때마다 id 없는
     * 새 엔티티를 만들어 넣으므로 그대로 두면 <b>제약 위반으로 터진다.</b>
     *
     * <p>조회 후 분기로 풀지 않는다 — 같은 구간을 동시에 잰 두 요청이 둘 다 "없다" 를 읽는다.
     * 판정은 제약을 쥔 DB 가 한 문장 안에서 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO car_leg_duration (from_lat, from_lng, to_lat, to_lng, minutes, measured_at)
                    VALUES (:fromLat, :fromLng, :toLat, :toLng, :minutes, :measuredAt) AS incoming
                    ON DUPLICATE KEY UPDATE
                        minutes = incoming.minutes,
                        measured_at = incoming.measured_at
                    """,
            nativeQuery = true)
    void upsert(
            @Param("fromLat") BigDecimal fromLat,
            @Param("fromLng") BigDecimal fromLng,
            @Param("toLat") BigDecimal toLat,
            @Param("toLng") BigDecimal toLng,
            @Param("minutes") int minutes,
            @Param("measuredAt") LocalDateTime measuredAt);
}
