package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPlace;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
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
public class FestivalPlaceRepositoryImpl implements FestivalPlaceRepository {

    private static final int BATCH_SIZE = 500;

    /**
     * 자연키가 겹치면 덮는다 — 지자체가 날짜·장소를 고쳐 다시 올리는 일이 있다.
     *
     * <p>{@code fetched_at} 을 함께 갱신하는 것이 핵심이다. 이번 회차가 받은 행은 전부 이 시각이 되고,
     * 그보다 오래된 행이 곧 "이번에 안 온 축제" 다({@link #deleteFetchedBefore}).
     */
    private static final String UPSERT_SQL =
            "INSERT INTO festival_place"
                    + " (region_id, name, venue, address, lat, lng, event_start, event_end,"
                    + "  description, host, tel, homepage_url, fetched_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + "  venue = VALUES(venue), address = VALUES(address),"
                    + "  lat = VALUES(lat), lng = VALUES(lng), event_end = VALUES(event_end),"
                    + "  description = VALUES(description), host = VALUES(host),"
                    + "  tel = VALUES(tel), homepage_url = VALUES(homepage_url),"
                    + "  fetched_at = VALUES(fetched_at)";

    private final FestivalPlaceJpaRepository festivalPlaceJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<FestivalPlace> findOpenOn(long regionId, LocalDate date, int limit) {
        return festivalPlaceJpaRepository.findOpenOn(regionId, date, PageRequest.ofSize(limit));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FestivalPlace> findById(long id) {
        return festivalPlaceJpaRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return festivalPlaceJpaRepository.count();
    }

    @Override
    @Transactional
    public int upsertAll(Collection<FestivalPlace> places) {
        if (places.isEmpty()) {
            return 0;
        }
        List<FestivalPlace> rows = List.copyOf(places);
        int affected = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<FestivalPlace> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(UPSERT_SQL, chunk, chunk.size(), (ps, place) -> {
                ps.setLong(1, place.getRegionId());
                ps.setString(2, place.getName());
                ps.setString(3, place.getVenue());
                ps.setString(4, place.getAddress());
                ps.setDouble(5, place.getLat());
                ps.setDouble(6, place.getLng());
                ps.setDate(7, Date.valueOf(place.getEventStart()));
                ps.setDate(8, Date.valueOf(place.getEventEnd()));
                ps.setString(9, place.getDescription());
                ps.setString(10, place.getHost());
                ps.setString(11, place.getTel());
                ps.setString(12, place.getHomepageUrl());
                ps.setTimestamp(13, Timestamp.valueOf(place.getFetchedAt()));
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
        return festivalPlaceJpaRepository.deleteByFetchedAtBefore(fetchedAt);
    }
}
