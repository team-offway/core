package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RelatedAttraction;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * port 구현(adapter) — Spring Data 에 위임.
 *
 * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약). 여기는 짧은 트랜잭션으로 읽고 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class RelatedAttractionRepositoryImpl implements RelatedAttractionRepository {

    private static final DateTimeFormatter BASE_YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final RelatedAttractionJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RelatedAttraction> findByHub(
            long regionId, String hubCode, String categoryLarge, int limit) {
        return jpaRepository.findByRegionIdAndHubCodeAndCategoryLargeOrderByRelatedRankAsc(
                regionId, hubCode, categoryLarge, PageRequest.ofSize(limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelatedAttraction> findByRegion(long regionId) {
        return jpaRepository.findByRegionId(regionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<YearMonth> latestBaseMonth(long regionId) {
        return Optional.ofNullable(jpaRepository.findMaxBaseYm(regionId))
                .map(ym -> YearMonth.parse(ym, BASE_YM));
    }

    /**
     * 그 지역 것을 지우고 새로 넣는다.
     *
     * <p><b>빈 목록이면 지우지 않는다.</b> 원본이 일시적으로 0건을 주는 날 기존 것을 날리면, 그 지역
     * 코스가 다음 달까지 좌표 군집으로 돌아간다 — 있는 값을 유지하는 편이 낫다.
     */
    @Override
    @Transactional
    public int replaceRegion(long regionId, YearMonth baseMonth, Collection<RelatedAttraction> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        jpaRepository.deleteByRegionId(regionId);
        jpaRepository.flush();
        return jpaRepository.saveAll(rows).size();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }
}
