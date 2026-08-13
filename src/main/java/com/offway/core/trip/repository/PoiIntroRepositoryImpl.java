package com.offway.core.trip.repository;

import com.offway.core.trip.domain.OpeningHours;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** port 구현(adapter). 엔티티가 없어 JDBC 로 직접 다룬다. */
@Repository
@RequiredArgsConstructor
public class PoiIntroRepositoryImpl implements PoiIntroRepository {

    /** 한 번에 넣는 크기. 코스 하나가 20건 안팎이라 넉넉하다. */
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, OpeningHours> findByContentIds(List<String> contentIds) {
        if (contentIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", contentIds.stream().map(id -> "?").toList());
        Map<String, OpeningHours> found = new HashMap<>();
        jdbcTemplate.query("SELECT content_id, use_time, rest_date FROM poi_intro WHERE content_id IN (" + placeholders + ")",
                rs -> {
                    found.put(rs.getString("content_id"),
                            new OpeningHours(rs.getString("use_time"), rs.getString("rest_date")));
                },
                contentIds.toArray());
        return found;
    }

    @Override
    public List<ContentRef> findMissing(int limit) {
        return jdbcTemplate.query("""
                SELECT DISTINCT s.poi_content_id, s.poi_content_type_id
                FROM slot s
                LEFT JOIN poi_intro p ON p.content_id = s.poi_content_id
                WHERE p.content_id IS NULL
                  AND s.poi_content_type_id IS NOT NULL
                LIMIT ?
                """, (rs, rowNum) -> new ContentRef(rs.getString(1), rs.getInt(2)), limit);
    }

    @Override
    public int upsertAll(Map<ContentRef, OpeningHours> hours, LocalDateTime fetchedAt) {
        List<Map.Entry<ContentRef, OpeningHours>> rows = List.copyOf(hours.entrySet());
        int saved = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<Map.Entry<ContentRef, OpeningHours>> chunk =
                    rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            int[] result = jdbcTemplate.batchUpdate("""
                    INSERT INTO poi_intro (content_id, content_type_id, use_time, rest_date, fetched_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE use_time = VALUES(use_time), rest_date = VALUES(rest_date),
                                            fetched_at = VALUES(fetched_at)
                    """, chunk, chunk.size(), (ps, entry) -> {
                ps.setString(1, entry.getKey().contentId());
                ps.setInt(2, entry.getKey().contentTypeId());
                ps.setString(3, entry.getValue().useTime());
                ps.setString(4, entry.getValue().restDate());
                ps.setObject(5, fetchedAt);
            })[0];
            for (int count : result) {
                saved += count < 0 ? 1 : count;
            }
        }
        return saved;
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM poi_intro", Long.class);
        return count == null ? 0 : count;
    }
}
