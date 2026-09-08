package com.offway.core.trip.repository;

import com.offway.core.trip.domain.CampingPlace;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
public class CampingPlaceRepositoryImpl implements CampingPlaceRepository {

    private static final int BATCH_SIZE = 500;

    /**
     * 자연키({@code external_id})가 겹치면 덮는다 — 야영장이 이름·시설을 고쳐 다시 올린다.
     *
     * <p>{@code region_id} 도 갱신 대상이다. 행정구역 개편이나 주소 정정으로 붙는 지역이 바뀔 수 있는데,
     * 그때 옛 지역에 남으면 엉뚱한 지역의 코스에 뜬다.
     *
     * <p>{@code fetched_at} 을 함께 갱신하는 것이 핵심이다. 이번 회차가 받은 행은 전부 이 시각이 되고,
     * 그보다 오래된 행이 곧 "이번에 안 온 야영장" 이다({@link #deleteFetchedBefore}).
     */
    private static final String UPSERT_SQL =
            "INSERT INTO camping_place"
                    + " (region_id, external_id, name, address, lat, lng, induty, image_url,"
                    + "  line_intro, intro, tel, homepage_url, oper_period, oper_days, reservation, fetched_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + "  region_id = VALUES(region_id), name = VALUES(name), address = VALUES(address),"
                    + "  lat = VALUES(lat), lng = VALUES(lng), induty = VALUES(induty),"
                    + "  image_url = VALUES(image_url), line_intro = VALUES(line_intro),"
                    + "  intro = VALUES(intro), tel = VALUES(tel), homepage_url = VALUES(homepage_url),"
                    + "  oper_period = VALUES(oper_period), oper_days = VALUES(oper_days),"
                    + "  reservation = VALUES(reservation), fetched_at = VALUES(fetched_at)";

    private final CampingPlaceJpaRepository campingPlaceJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<CampingPlace> findCandidates(long regionId, int limit) {
        return campingPlaceJpaRepository.findCandidates(regionId, PageRequest.ofSize(limit));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CampingPlace> findById(long id) {
        return campingPlaceJpaRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return campingPlaceJpaRepository.count();
    }

    @Override
    @Transactional
    public int upsertAll(Collection<CampingPlace> places) {
        if (places.isEmpty()) {
            return 0;
        }
        List<CampingPlace> rows = List.copyOf(places);
        int affected = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<CampingPlace> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(UPSERT_SQL, chunk, chunk.size(), (ps, place) -> {
                ps.setLong(1, place.getRegionId());
                ps.setString(2, place.getExternalId());
                ps.setString(3, place.getName());
                ps.setString(4, place.getAddress());
                ps.setDouble(5, place.getLat());
                ps.setDouble(6, place.getLng());
                ps.setString(7, place.getInduty());
                ps.setString(8, place.getImageUrl());
                ps.setString(9, place.getLineIntro());
                ps.setString(10, place.getIntro());
                ps.setString(11, place.getTel());
                ps.setString(12, place.getHomepageUrl());
                ps.setString(13, place.getOperPeriod());
                ps.setString(14, place.getOperDays());
                ps.setString(15, place.getReservation());
                ps.setTimestamp(16, Timestamp.valueOf(place.getFetchedAt()));
            });
            // 드라이버가 준 건수를 쓰지 않고 넘긴 행을 센다. MySQL 은 ON DUPLICATE KEY UPDATE 에서
            // 새로 넣으면 1, 고치면 2, 값이 같아 안 바뀌면 0을 돌려주는데 우리가 알고 싶은 것은
            // "몇 건을 다뤘나" 다 — 그 규칙으로 더하면 고친 행이 두 건으로 세어진다.
            affected += chunk.size();
        }
        return affected;
    }

    @Override
    @Transactional
    public int deleteFetchedBefore(LocalDateTime fetchedAt) {
        return campingPlaceJpaRepository.deleteByFetchedAtBefore(fetchedAt);
    }
}
