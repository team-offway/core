package com.offway.core.trip.repository;

import com.offway.core.trip.domain.AttractionCrowdForecast;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 읽기만 맡는다 — 쓰기는 adapter 가 JdbcTemplate 으로 한다.
 *
 * <p>지우기를 여기 두지 않는 이유가 있다. 파생 삭제는 영속성 컨텍스트에 쌓였다가 flush 시점에 나가는데,
 * 삽입은 JdbcTemplate 이라 커넥션으로 곧장 간다. 한 트랜잭션에서 섞으면 순서가 뒤집혀 방금 넣은 행이
 * 지워진다.
 */
public interface AttractionCrowdForecastJpaRepository extends JpaRepository<AttractionCrowdForecast, Long> {

    List<AttractionCrowdForecast> findByRegionIdAndBaseDateIn(long regionId, Collection<LocalDate> dates);
}
