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

    /**
     * 일감은 <b>한 번도 안 받은 것</b> 과 <b>빈 채로 오래된 것</b> 둘이다.
     *
     * <p>빈 행을 영원히 제외하면 원본이 나중에 운영시간을 채워도 우리는 영영 모른다. 그렇다고 매 회차 다시
     * 물으면 예산을 태우므로 {@code fetched_at} 으로 간격을 둔다.
     *
     * <p>순서를 정한다 — 예산이 유한하면 <b>무엇을 먼저 채우는지</b>가 화면을 가른다. 한 번도 안 받은 것이
     * 앞이고(재시도가 앞줄을 차지하면 아직 아무것도 없는 화면이 방치된다), 그 안에서는 최근 슬롯이
     * 앞이다(방금 만든 코스가 지금 보고 있는 코스다). 순서를 안 정하면 DB 가 주는 대로라 같은 예산으로
     * 무엇이 채워질지 예측할 수 없다.
     *
     * <p>{@code DISTINCT} 가 아니라 콘텐츠 id 로 묶는다 — 같은 콘텐츠가 타입이 다른 슬롯 둘에 실리면
     * {@code DISTINCT} 는 두 줄을 주는데, {@code poi_intro} 는 콘텐츠당 한 행이라 예산만 두 번 쓴다.
     */
    @Override
    public List<ContentRef> findMissing(int limit, LocalDateTime emptyRetryBefore) {
        return jdbcTemplate.query("""
                SELECT s.poi_content_id,
                       MAX(s.poi_content_type_id) AS content_type_id,
                       (MAX(p.content_id) IS NULL) AS never_fetched,
                       MAX(s.id) AS newest_slot_id
                FROM slot s
                LEFT JOIN poi_intro p ON p.content_id = s.poi_content_id
                WHERE s.poi_content_type_id IS NOT NULL
                  AND (p.content_id IS NULL
                       OR (p.use_time IS NULL AND p.rest_date IS NULL AND p.fetched_at < ?))
                GROUP BY s.poi_content_id
                ORDER BY never_fetched DESC, newest_slot_id DESC
                LIMIT ?
                """, (rs, rowNum) -> new ContentRef(rs.getString(1), rs.getInt(2)), emptyRetryBefore, limit);
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
