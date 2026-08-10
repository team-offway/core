package com.offway.core.trip.repository;

import com.offway.core.trip.domain.HeritageGroup;
import com.offway.core.trip.domain.HeritagePlace;
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
            int[] result = jdbcTemplate.batchUpdate(INSERT_SQL, chunk, chunk.size(), (ps, place) -> {
                ps.setLong(1, place.getRegionId());
                ps.setString(2, place.getKind());
                ps.setString(3, place.getGroup().name());
                ps.setString(4, place.getName());
                ps.setString(5, place.getAddress());
                ps.setDouble(6, place.getLat());
                ps.setDouble(7, place.getLng());
                ps.setString(8, place.getImageUrl());
                ps.setString(9, place.getDescription());
            })[0];
            for (int count : result) {
                inserted += count < 0 ? 1 : count;
            }
        }
        return inserted;
    }
}
