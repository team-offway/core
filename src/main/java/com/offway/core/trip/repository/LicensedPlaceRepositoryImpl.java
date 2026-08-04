package com.offway.core.trip.repository;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceKind;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — 조회는 Spring Data 에, 대량 적재는 JDBC 배치에 위임한다. */
@Repository
@RequiredArgsConstructor
public class LicensedPlaceRepositoryImpl implements LicensedPlaceRepository {

    /**
     * 한 번에 보낼 행 수. 너무 크면 패킷 상한(MySQL {@code max_allowed_packet})에 걸리고, 너무 작으면
     * 왕복이 늘어난다.
     */
    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT_SQL =
            "INSERT INTO licensed_place (region_id, kind, category, name, address, tel, lat, lng)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private final LicensedPlaceJpaRepository licensedPlaceJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<LicensedPlace> findByRegionAndKind(long regionId, PlaceKind kind) {
        return licensedPlaceJpaRepository.findByRegionIdAndKind(regionId, kind);
    }

    @Override
    public Optional<LicensedPlace> findById(long id) {
        return licensedPlaceJpaRepository.findById(id);
    }

    @Override
    public long count() {
        return licensedPlaceJpaRepository.count();
    }

    /**
     * JPA 대신 JDBC 배치를 쓰는 이유 — {@code saveAll} 은 영속성 컨텍스트에 16만 엔티티를 쌓고 건건이 INSERT 를
     * 날려 부팅이 분 단위로 늘어난다. 여기서 만드는 행은 이후 수정되지 않는 읽기 전용 레퍼런스라 영속성 컨텍스트가 필요 없다.
     */
    @Override
    public int saveAll(List<LicensedPlace> places) {
        int inserted = 0;
        for (int start = 0; start < places.size(); start += BATCH_SIZE) {
            List<LicensedPlace> chunk = places.subList(start, Math.min(start + BATCH_SIZE, places.size()));
            int[] result = jdbcTemplate.batchUpdate(INSERT_SQL, chunk, chunk.size(), (ps, place) -> {
                ps.setLong(1, place.getRegionId());
                ps.setString(2, place.getKind().name());
                ps.setString(3, place.getCategory().name());
                ps.setString(4, place.getName());
                ps.setString(5, place.getAddress());
                ps.setString(6, place.getTel());
                ps.setDouble(7, place.getLat());
                ps.setDouble(8, place.getLng());
            })[0];
            for (int count : result) {
                // 드라이버가 건수를 모른다고 답할 수 있다(SUCCESS_NO_INFO). 그래도 실패는 아니므로 1건으로 센다.
                inserted += count < 0 ? 1 : count;
            }
        }
        return inserted;
    }
}
