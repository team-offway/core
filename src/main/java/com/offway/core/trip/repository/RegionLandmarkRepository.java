package com.offway.core.trip.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 지역의 <b>대표 볼거리 이름</b>을 뽑는다(#140) — 지역 한 줄 소개의 재료.
 *
 * <p><b>지어내지 않는다.</b> 소개 문구를 사람이 쓰면 89곳 카피를 만들어야 하고, 그건 실재하는 지역에 대한
 * 주장이라 틀리면 그대로 사용자에게 나간다. 대신 우리가 이미 가진 사실 — 그 지역에 실제로 있는 국가유산과
 * 볼거리 이름 — 을 조합한다.
 *
 * <p><b>종목 순서가 곧 대표성이다.</b> 국보·사적·명승은 그 자체가 지역을 대표하는 자리이고, 시도지정은
 * 그보다 좁다. 같은 지역에서 국보가 있으면 그것이 먼저다.
 */
@Repository
@RequiredArgsConstructor
public class RegionLandmarkRepository {

    /**
     * 대표성이 높은 순서. 앞에 있을수록 그 지역을 대표한다.
     *
     * <p>사적·명승을 보물보다 앞에 둔다 — 보물에는 석탑·불상처럼 <b>건물 안의 작은 것</b>이 많은데,
     * 사적·명승은 자리 자체가 목적지다.
     */
    private static final List<String> KIND_RANK = List.of(
            "국보", "사적", "명승", "천연기념물", "보물", "국가민속문화유산", "국가등록문화유산");

    private final JdbcTemplate jdbcTemplate;

    /**
     * 지역별 대표 볼거리 이름 — 지역당 최대 {@code limit} 개.
     *
     * <p>한 번의 질의로 전 지역을 가져온다. 89번 물으면 부팅이 그만큼 느려진다.
     */
    public Map<Long, List<String>> topHeritageNames(int limit) {
        String ranked = String.join(",", KIND_RANK.stream().map(kind -> "'" + kind + "'").toList());
        Map<Long, List<String>> byRegion = new HashMap<>();
        jdbcTemplate.query("""
                SELECT region_id, name FROM heritage_place
                WHERE kind IN (%s)
                ORDER BY region_id, FIELD(kind, %s), id
                """.formatted(ranked, ranked), rs -> {
            List<String> names = byRegion.computeIfAbsent(rs.getLong("region_id"), key -> new ArrayList<>());
            if (names.size() < limit) {
                names.add(rs.getString("name"));
            }
        });
        return byRegion;
    }

    /**
     * 국가유산이 없는 지역의 대체 — 관광 콘텐츠성이 높은 인허가 볼거리(전통사찰·박물관 등).
     *
     * <p>실제로 한 곳(대구 서구)이 여기 해당한다. 그 지역의 국가유산이 무형유산뿐이라 방문 대상이 없다.
     */
    public Map<Long, List<String>> topLicensedSightNames(int limit) {
        Map<Long, List<String>> byRegion = new HashMap<>();
        jdbcTemplate.query("""
                SELECT region_id, name FROM licensed_place
                WHERE kind = 'SIGHT'
                ORDER BY region_id, fitness_rank, name
                """, rs -> {
            List<String> names = byRegion.computeIfAbsent(rs.getLong("region_id"), key -> new ArrayList<>());
            if (names.size() < limit) {
                names.add(rs.getString("name"));
            }
        });
        return byRegion;
    }
}
