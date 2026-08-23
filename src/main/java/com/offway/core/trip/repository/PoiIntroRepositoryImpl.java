package com.offway.core.trip.repository;

import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.domain.PoiIntro;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** port 구현(adapter). 엔티티가 없어 JDBC 로 직접 다룬다. */
@Repository
@RequiredArgsConstructor
public class PoiIntroRepositoryImpl implements PoiIntroRepository {

    /** 한 번에 넣는 크기. 코스 하나가 20건 안팎이라 넉넉하다. */
    private static final int BATCH_SIZE = 500;

    /**
     * 상세를 못 받는 콘텐츠 타입 — 부르면 콜만 쓴다.
     *
     * <p>실측(2026-08-24)에서 타입 28(레포츠·야영장)은 표본 20건 중 19건이 빈 응답이었다.
     * {@code resultCode} 는 성공인데 {@code items} 가 비어 오고, 타입을 바꿔 불러도 같았다.
     */
    private static final int TYPE_WITHOUT_INTRO = 28;

    /** 부제가 읽는 칸 전부. 컬럼이 늘 때 이 한 줄만 고치면 조회·매핑이 함께 따라온다. */
    private static final String INTRO_COLUMNS =
            "use_time, rest_date, parking, fee, signature_menu, menus, check_in, check_out, room_count, reservation";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, OpeningHours> findByContentIds(List<String> contentIds) {
        if (contentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, OpeningHours> found = new HashMap<>();
        jdbcTemplate.query(
                "SELECT content_id, use_time, rest_date FROM poi_intro WHERE content_id IN (" + placeholders(contentIds) + ")",
                rs -> {
                    found.put(rs.getString("content_id"),
                            new OpeningHours(rs.getString("use_time"), rs.getString("rest_date")));
                },
                contentIds.toArray());
        return found;
    }

    @Override
    public Map<String, PoiIntro> findIntros(List<String> contentIds) {
        if (contentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, PoiIntro> found = new HashMap<>();
        jdbcTemplate.query(
                "SELECT content_id, " + INTRO_COLUMNS + " FROM poi_intro WHERE content_id IN ("
                        + placeholders(contentIds) + ")",
                rs -> {
                    found.put(rs.getString("content_id"), toIntro(rs));
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
                """, (rs, rowNum) -> ContentRef.of(rs.getString(1), rs.getInt(2)), emptyRetryBefore, limit);
    }

    /**
     * 지역·칩마다 앞의 몇 건만 일감으로 삼는다 — 홈이 그만큼만 보여주기 때문이다(#305).
     *
     * <p><b>순위를 {@code region_poi.id} 로 매긴다.</b> 적재가 통째 교체라 그 순서가 곧 외부가 준 순서이고,
     * 홈이 카드를 고르는 순서와 같아야 <b>받아 둔 것과 보여주는 것이 어긋나지 않는다.</b>
     *
     * <p>사진 없는 장소와 상세를 못 받는 타입은 애초에 빼고 순위를 매긴다. 순위를 매긴 뒤 걸러내면
     * 앞자리를 그들이 차지해 실제로 받는 건수가 줄어든다.
     */
    @Override
    public List<ContentRef> findMissingForCards(int limit, int perCategory, LocalDateTime emptyRetryBefore) {
        return jdbcTemplate.query("""
                SELECT content_id, content_type_id, category
                FROM (
                    SELECT rp.content_id,
                           rp.content_type_id,
                           rp.category,
                           ROW_NUMBER() OVER (PARTITION BY rp.region_id, rp.category ORDER BY rp.id) AS rank_in_chip,
                           (p.content_id IS NULL) AS never_fetched
                    FROM region_poi rp
                    LEFT JOIN poi_intro p ON p.content_id = rp.content_id
                    WHERE rp.image_url IS NOT NULL AND rp.image_url <> ''
                      AND rp.content_type_id <> ?
                      AND (p.content_id IS NULL OR (p.fetched_at < ? AND %s))
                ) ranked
                WHERE rank_in_chip <= ?
                ORDER BY never_fetched DESC, content_id
                LIMIT ?
                """.formatted(allColumnsNull()),
                (rs, rowNum) -> new ContentRef(
                        rs.getString("content_id"),
                        rs.getInt("content_type_id"),
                        // 모르는 이름이면 null 이다. 칩은 로그·집계에만 쓰이므로 그 한 건 때문에
                        // 배치를 세울 이유가 없다 — valueOf 였다면 예외로 전부 멈춘다.
                        Category.byName(rs.getString("category")).orElse(null)),
                TYPE_WITHOUT_INTRO, emptyRetryBefore, perCategory, limit);
    }

    @Override
    public int upsertAll(Map<ContentRef, PoiIntro> intros, LocalDateTime fetchedAt) {
        List<Map.Entry<ContentRef, PoiIntro>> rows = List.copyOf(intros.entrySet());
        int saved = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<Map.Entry<ContentRef, PoiIntro>> chunk =
                    rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            int[] result = jdbcTemplate.batchUpdate("""
                    INSERT INTO poi_intro (content_id, content_type_id, use_time, rest_date, parking, fee,
                                           signature_menu, menus, check_in, check_out, room_count, reservation,
                                           fetched_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE use_time = VALUES(use_time), rest_date = VALUES(rest_date),
                                            parking = VALUES(parking), fee = VALUES(fee),
                                            signature_menu = VALUES(signature_menu), menus = VALUES(menus),
                                            check_in = VALUES(check_in), check_out = VALUES(check_out),
                                            room_count = VALUES(room_count), reservation = VALUES(reservation),
                                            fetched_at = VALUES(fetched_at)
                    """, chunk, chunk.size(), (ps, entry) -> {
                PoiIntro intro = entry.getValue();
                ps.setString(1, entry.getKey().contentId());
                ps.setInt(2, entry.getKey().contentTypeId());
                ps.setString(3, intro.useTime());
                ps.setString(4, intro.restDate());
                ps.setString(5, intro.parking());
                ps.setString(6, intro.fee());
                ps.setString(7, intro.signatureMenu());
                ps.setString(8, intro.menus());
                ps.setString(9, intro.checkIn());
                ps.setString(10, intro.checkOut());
                ps.setString(11, intro.roomCount());
                ps.setString(12, intro.reservation());
                ps.setObject(13, fetchedAt);
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

    private static String placeholders(List<String> values) {
        return String.join(",", values.stream().map(value -> "?").toList());
    }

    /**
     * "이 행은 아직 아무것도 못 받았다" 를 SQL 로 — 재시도 대상 판정이다.
     *
     * <p>컬럼 목록에서 만들어 <b>새 칸이 늘 때 자동으로 따라온다.</b> 손으로 나열하면 컬럼을 더한 사람이
     * 이 조건을 빠뜨리고, 그러면 값이 하나만 있는 행도 영원히 재시도 대상이 된다.
     */
    private static String allColumnsNull() {
        return Arrays.stream(INTRO_COLUMNS.split(",\\s*"))
                .map(column -> "p." + column + " IS NULL")
                .collect(Collectors.joining(" AND "));
    }

    private static PoiIntro toIntro(ResultSet rs) throws SQLException {
        return PoiIntro.builder()
                .useTime(rs.getString("use_time"))
                .restDate(rs.getString("rest_date"))
                .parking(rs.getString("parking"))
                .fee(rs.getString("fee"))
                .signatureMenu(rs.getString("signature_menu"))
                .menus(rs.getString("menus"))
                .checkIn(rs.getString("check_in"))
                .checkOut(rs.getString("check_out"))
                .roomCount(rs.getString("room_count"))
                .reservation(rs.getString("reservation"))
                .build();
    }
}
