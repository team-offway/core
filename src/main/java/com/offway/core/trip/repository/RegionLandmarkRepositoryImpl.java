package com.offway.core.trip.repository;

import com.offway.core.trip.domain.PlaceKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link RegionLandmarkRepository} 의 SQL 구현.
 *
 * <p>엔티티를 두지 않는다 — 뽑는 것이 이름 목록뿐이고 도메인 규칙이 없다. 지역별 상위 N 개는 SQL 이
 * 한 번에 답한다.
 *
 * <p><b>종목 순서가 곧 대표성이다.</b> 국보·사적·명승은 그 자체가 지역을 대표하는 자리이고, 시도지정은
 * 그보다 좁다. 같은 지역에서 국보가 있으면 그것이 먼저다.
 */
@Repository
@RequiredArgsConstructor
public class RegionLandmarkRepositoryImpl implements RegionLandmarkRepository {

    /**
     * 대표성이 높은 순서. 앞에 있을수록 그 지역을 대표한다.
     *
     * <p>사적·명승을 보물보다 앞에 둔다 — 보물에는 석탑·불상처럼 <b>건물 안의 작은 것</b>이 많은데,
     * 사적·명승은 자리 자체가 목적지다.
     */
    private static final List<String> KIND_RANK = List.of(
            "국보", "사적", "명승", "천연기념물", "보물", "국가민속문화유산", "국가등록문화유산");

    private final JdbcTemplate jdbcTemplate;

    /** 한 번의 질의로 전 지역을 가져온다. 89번 물으면 부팅이 그만큼 느려진다. */
    @Override
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

    @Override
    public Map<Long, List<String>> topLicensedSightNames(int limit) {
        Map<Long, List<String>> byRegion = new HashMap<>();
        // kind 는 PlaceKind 를 STRING 으로 저장한 값이다. 이름을 SQL 에 박으면 enum 상수를 바꿔도
        // 컴파일이 통과해, 이 조회만 조용히 0건이 된다.
        jdbcTemplate.query("""
                SELECT region_id, name FROM licensed_place
                WHERE kind = ?
                ORDER BY region_id, fitness_rank, name
                """, rs -> {
            List<String> names = byRegion.computeIfAbsent(rs.getLong("region_id"), key -> new ArrayList<>());
            if (names.size() < limit) {
                names.add(rs.getString("name"));
            }
        }, PlaceKind.SIGHT.name());
        return byRegion;
    }
}
