package com.offway.core.trip.repository;

import com.offway.core.trip.domain.PetFriendlyPlace;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * port 구현(adapter) — Spring Data 와 JdbcTemplate 에 위임.
 *
 * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약). 여기는 짧은 트랜잭션으로 읽고 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class PetFriendlyPlaceRepositoryImpl implements PetFriendlyPlaceRepository {

    private static final int BATCH_SIZE = 500;

    /**
     * 자연키({@code content_id})가 겹치면 덮는다 — 시설이 동반 조건을 고쳐 올린다.
     *
     * <p>{@code region_id} 도 갱신 대상이다. 행정구역 개편이나 주소 정정으로 붙는 지역이 바뀔 수 있는데,
     * 그때 옛 지역에 남으면 엉뚱한 지역의 코스에 뜬다(고캠핑과 같은 판단).
     *
     * <p>{@code fetched_at} 을 함께 갱신하는 것이 핵심이다. 이번 회차가 받은 행은 전부 이 시각이 되고,
     * 그보다 오래된 행이 곧 "이번에 안 온 장소" 다({@link #deleteFetchedBefore}).
     */
    private static final String UPSERT_SQL =
            "INSERT INTO pet_friendly_place"
                    + " (content_id, region_id, name, accompany_area, accompany_pet, required_matter,"
                    + "  etc_info, risk_matter, facilities, provided_items, rental_items, fetched_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + "  region_id = VALUES(region_id), name = VALUES(name),"
                    + "  accompany_area = VALUES(accompany_area), accompany_pet = VALUES(accompany_pet),"
                    + "  required_matter = VALUES(required_matter), etc_info = VALUES(etc_info),"
                    + "  risk_matter = VALUES(risk_matter), facilities = VALUES(facilities),"
                    + "  provided_items = VALUES(provided_items), rental_items = VALUES(rental_items),"
                    + "  fetched_at = VALUES(fetched_at)";

    private final PetFriendlyPlaceJpaRepository petFriendlyPlaceJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<PetFriendlyPlace> findByRegionId(long regionId) {
        return petFriendlyPlaceJpaRepository.findByRegionIdOrderByIdAsc(regionId);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return petFriendlyPlaceJpaRepository.count();
    }

    @Override
    @Transactional
    public int upsertAll(Collection<PetFriendlyPlace> places) {
        if (places.isEmpty()) {
            return 0;
        }
        List<PetFriendlyPlace> rows = List.copyOf(places);
        int affected = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<PetFriendlyPlace> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(UPSERT_SQL, chunk, chunk.size(), (ps, place) -> {
                ps.setString(1, place.getContentId());
                ps.setLong(2, place.getRegionId());
                ps.setString(3, place.getName());
                ps.setString(4, place.getAccompanyArea());
                ps.setString(5, place.getAccompanyPet());
                ps.setString(6, place.getRequiredMatter());
                ps.setString(7, place.getEtcInfo());
                ps.setString(8, place.getRiskMatter());
                ps.setString(9, place.getFacilities());
                ps.setString(10, place.getProvidedItems());
                ps.setString(11, place.getRentalItems());
                ps.setTimestamp(12, Timestamp.valueOf(place.getFetchedAt()));
            });
            // 드라이버가 준 건수를 쓰지 않고 넘긴 행을 센다 — MySQL 은 새로 넣으면 1, 고치면 2,
            // 값이 같으면 0 을 돌려주는데 우리가 알고 싶은 것은 "몇 건을 다뤘나" 다(고캠핑과 같은 이유).
            affected += chunk.size();
        }
        return affected;
    }

    @Override
    @Transactional
    public int deleteFetchedBefore(LocalDateTime fetchedAt) {
        return petFriendlyPlaceJpaRepository.deleteByFetchedAtBefore(fetchedAt);
    }
}
