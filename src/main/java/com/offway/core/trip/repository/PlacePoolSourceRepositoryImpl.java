package com.offway.core.trip.repository;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** port 구현(adapter). 행이 하나뿐이라 JPA 엔티티를 두지 않고 JDBC 로 직접 다룬다. */
@Repository
@RequiredArgsConstructor
public class PlacePoolSourceRepositoryImpl implements PlacePoolSourceRepository {

    /** 기록은 언제나 한 행이다 — 적재 대상 파일이 하나뿐이다. */
    private static final int SINGLETON_ID = 1;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<String> findChecksum() {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT checksum FROM place_pool_source WHERE id = ?", String.class, SINGLETON_ID));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty(); // 아직 적재된 적 없음
        }
    }

    @Override
    public void record(String checksum, int placeCount) {
        // H2·MySQL 양쪽에서 도는 UPSERT 를 쓰지 않고 지운 뒤 넣는다. 행이 하나뿐이라 비용이 없고,
        // MERGE 방언 차이를 피할 수 있다. 호출자의 트랜잭션에 참여하므로 중간 상태가 보이지 않는다.
        jdbcTemplate.update("DELETE FROM place_pool_source WHERE id = ?", SINGLETON_ID);
        jdbcTemplate.update(
                "INSERT INTO place_pool_source (id, checksum, place_count, loaded_at) VALUES (?, ?, ?, ?)",
                SINGLETON_ID, checksum, placeCount, java.sql.Timestamp.from(Instant.now()));
    }
}
