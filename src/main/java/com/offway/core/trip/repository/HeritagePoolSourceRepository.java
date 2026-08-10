package com.offway.core.trip.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 어떤 파일로 국가유산 풀을 채웠는지 기록한다(#160).
 *
 * <p>엔티티를 두지 않는다 — 행이 언제나 하나이고 도메인 규칙이 없다. JDBC 두 줄이 JPA 엔티티·리포지토리
 * 한 벌보다 읽기 쉽다.
 */
@Repository
@RequiredArgsConstructor
public class HeritagePoolSourceRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 마지막으로 적재한 파일의 체크섬. 한 번도 안 채웠으면 비어 있음. */
    public Optional<String> findChecksum() {
        return jdbcTemplate
                .query("SELECT checksum FROM heritage_pool_source ORDER BY id DESC LIMIT 1",
                        (rs, rowNum) -> rs.getString(1))
                .stream()
                .findFirst();
    }

    /** 새로 채운 사실을 남긴다. 이전 기록은 지운다 — 최신 하나만 의미가 있다. */
    public void record(String checksum, int loadedCount) {
        jdbcTemplate.update("DELETE FROM heritage_pool_source");
        jdbcTemplate.update(
                "INSERT INTO heritage_pool_source (checksum, loaded_count, loaded_at) VALUES (?, ?, ?)",
                checksum, loadedCount, LocalDateTime.now());
    }
}
