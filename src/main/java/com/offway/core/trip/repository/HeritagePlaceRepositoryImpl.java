package com.offway.core.trip.repository;

import com.offway.core.trip.domain.HeritageGroup;
import com.offway.core.trip.domain.HeritagePlace;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HeritagePlaceRepositoryImpl implements HeritagePlaceRepository {

    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT_SQL =
            "INSERT INTO heritage_place"
                    + " (region_id, kind, group_code, name, address, lat, lng, image_url, description)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** 방문 가능한 대분류. enum 이 판정을 소유하므로 여기서 목록을 다시 적지 않는다. */
    private static final List<HeritageGroup> VISITABLE_GROUPS =
            Arrays.stream(HeritageGroup.values()).filter(HeritageGroup::isVisitable).toList();

    private final HeritagePlaceJpaRepository heritagePlaceJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<HeritagePlace> findVisitableCandidates(long regionId, int limit) {
        return heritagePlaceJpaRepository.findByRegionIdAndGroupInOrderByIdAsc(
                regionId, VISITABLE_GROUPS, PageRequest.ofSize(limit));
    }

    @Override
    public Optional<HeritagePlace> findById(long id) {
        return heritagePlaceJpaRepository.findById(id);
    }

    @Override
    public long count() {
        return heritagePlaceJpaRepository.count();
    }

    @Override
    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM heritage_place");
    }

    @Override
    public int saveAll(List<HeritagePlace> places) {
        int inserted = 0;
        for (int start = 0; start < places.size(); start += BATCH_SIZE) {
            List<HeritagePlace> chunk = places.subList(start, Math.min(start + BATCH_SIZE, places.size()));
            // 반환은 [내부배치][행] 2차원이다. [0] 만 보면 지금은 맞는다 — 배치 인자를 chunk.size() 로 줘서
            // 내부 배치가 하나뿐이기 때문이다. 그 우연에 기대면 나중에 배치 크기를 나누는 순간 집계가
            // 조용히 어긋나고, 건수 검증(replaceAll)이 엉뚱한 이유로 실패한다. 전부 훑는다.
            int[][] results = jdbcTemplate.batchUpdate(INSERT_SQL, chunk, chunk.size(), (ps, place) -> {
                ps.setLong(1, place.getRegionId());
                ps.setString(2, place.getKind());
                ps.setString(3, place.getGroup().name());
                ps.setString(4, place.getName());
                ps.setString(5, place.getAddress());
                ps.setDouble(6, place.getLat());
                ps.setDouble(7, place.getLng());
                ps.setString(8, place.getImageUrl());
                ps.setString(9, place.getDescription());
            });
            for (int[] batch : results) {
                for (int count : batch) {
                    // EXECUTE_FAILED(-3)를 성공 1건으로 세면 안 된다. 부분 적재를 잡으라고 둔 검증이
                    // 바로 그 상황을 못 잡게 된다. 음수를 뭉뚱그리던 것이 그랬다.
                    if (count == Statement.EXECUTE_FAILED) {
                        throw new IllegalStateException("국가유산 행 적재가 실패했습니다");
                    }
                    inserted += count == Statement.SUCCESS_NO_INFO ? 1 : count;
                }
            }
        }
        return inserted;
    }
}
