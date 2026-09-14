package com.offway.core.trip.repository;

import com.offway.core.trip.domain.AttractionCrowdForecast;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * port 구현(adapter) — Spring Data 와 JdbcTemplate 에 위임.
 *
 * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약). 여기는 짧은 트랜잭션으로 읽고 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class AttractionCrowdForecastRepositoryImpl implements AttractionCrowdForecastRepository {

    private static final int BATCH_SIZE = 1_000;

    /**
     * 지우고 넣으므로 충돌이 없지만 그래도 upsert 로 둔다.
     *
     * <p>같은 회차 안에 같은 (관광지, 날짜)가 두 번 오면 — 외부가 중복 행을 주면 — {@code INSERT} 는
     * 유니크 제약에 걸려 그 지역이 통째로 실패한다. 값이 같은 중복 때문에 지역 하나를 잃을 이유가 없다.
     */
    private static final String INSERT_SQL =
            "INSERT INTO attraction_crowd_forecast"
                    + " (region_id, attraction_name, base_date, rate, fetched_at)"
                    + " VALUES (?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE rate = VALUES(rate), fetched_at = VALUES(fetched_at)";

    /**
     * <b>지우는 것도 JDBC 로 한다.</b> JPA 파생 삭제는 영속성 컨텍스트에 쌓였다가 flush 시점에 나가는데,
     * 아래 삽입은 JdbcTemplate 이라 커넥션으로 곧장 간다. 한 트랜잭션 안에서 섞으면 <b>삽입이 먼저 나가고
     * 삭제가 뒤따라</b> 방금 넣은 행까지 지운다 — 통합 테스트가 실제로 그렇게 깨졌다.
     */
    private static final String DELETE_REGION_SQL =
            "DELETE FROM attraction_crowd_forecast WHERE region_id = ?";

    private static final String DELETE_BEFORE_SQL =
            "DELETE FROM attraction_crowd_forecast WHERE base_date < ?";

    private final AttractionCrowdForecastJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<AttractionCrowdForecast> findByRegionAndDates(long regionId, Collection<LocalDate> dates) {
        if (dates.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByRegionIdAndBaseDateIn(regionId, dates);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }

    /**
     * 지우고 넣는 것이 <b>한 트랜잭션</b>이다.
     *
     * <p>나뉘면 그 사이에 읽는 요청이 그 지역의 칩을 통째로 잃는다. 예보는 덤이라 없어도 화면은
     * 나가지만, 우리가 만든 공백을 사용자가 보는 것은 다른 이야기다.
     */
    @Override
    @Transactional
    public int replaceRegion(long regionId, Collection<AttractionCrowdForecast> forecasts) {
        if (forecasts.isEmpty()) {
            // port 계약이 금지한 호출이다. 조용히 그 지역을 비우지 않고 불변식 위반으로 드러낸다.
            throw new IllegalArgumentException("빈 예보로 지역을 갈아 끼울 수 없습니다: regionId=" + regionId);
        }
        jdbcTemplate.update(DELETE_REGION_SQL, regionId);
        List<AttractionCrowdForecast> rows = List.copyOf(forecasts);
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<AttractionCrowdForecast> chunk =
                    rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(INSERT_SQL, chunk, chunk.size(), (ps, row) -> {
                ps.setLong(1, row.getRegionId());
                ps.setString(2, row.getAttractionName());
                ps.setDate(3, Date.valueOf(row.getBaseDate()));
                ps.setDouble(4, row.getRate());
                ps.setTimestamp(5, Timestamp.valueOf(row.getFetchedAt()));
            });
        }
        return rows.size();
    }

    @Override
    @Transactional
    public int deleteBefore(LocalDate date) {
        return jdbcTemplate.update(DELETE_BEFORE_SQL, Date.valueOf(date));
    }
}
