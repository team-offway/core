package com.offway.core.trip.repository;

import com.offway.core.trip.domain.OpeningHours;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 장소 운영시간·휴무일 저장소(#157).
 *
 * <p>엔티티를 두지 않는다 — 행이 콘텐츠 하나당 하나이고 도메인 규칙이 없다. 조회는 "이 코스의 콘텐츠들"
 * 처럼 항상 묶음이라 JPA 로 얻을 것도 없다.
 */
@Repository
@RequiredArgsConstructor
public class PoiIntroRepository {

    /** 한 번에 넣는 크기. 코스 하나가 20건 안팎이라 넉넉하다. */
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    /** 콘텐츠 id 로 운영시간을 찾는다. 없는 것은 키가 없다 — 호출자가 "아직 안 받았다" 로 읽는다. */
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
     * 아직 안 받은 콘텐츠를 코스 슬롯에서 찾는다 — <b>슬롯 테이블이 곧 일감 목록이다</b>.
     *
     * <p>별도 큐를 두지 않는다. 우리가 운영시간을 알아야 하는 콘텐츠는 정확히 "코스에 실제로 쓰인 것" 이고,
     * 그건 이미 슬롯에 남아 있다. 큐를 만들면 슬롯과 두 곳이 되어 어긋난다.
     *
     * <p>타입이 없는 슬롯(이 기능 이전 코스·우리 DB 출처)은 제외한다 — 타입 없이는 detailIntro2 를 못 부른다.
     */
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

    /** 아직 안 받은 콘텐츠 한 건 — 타입이 있어야 detailIntro2 를 부를 수 있다. */
    public record ContentRef(String contentId, int contentTypeId) {
    }

    /** 받은 것을 넣는다. 같은 콘텐츠를 다시 받으면 덮어쓴다. */
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

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM poi_intro", Long.class);
        return count == null ? 0 : count;
    }
}
