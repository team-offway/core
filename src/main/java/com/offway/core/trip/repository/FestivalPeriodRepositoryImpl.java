package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPeriod;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * port 구현(adapter) — Spring Data 에 위임.
 *
 * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약). 여기는 짧은 트랜잭션으로 쓰기만 한다.
 */
@Repository
@RequiredArgsConstructor
public class FestivalPeriodRepositoryImpl implements FestivalPeriodRepository {

    private final FestivalPeriodJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<String, FestivalPeriod> findByContentIds(Collection<String> contentIds) {
        if (contentIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findByContentIdIn(contentIds).stream()
                .collect(Collectors.toMap(FestivalPeriod::getContentId, Function.identity()));
    }

    /**
     * <b>{@code saveAll} 로 충분하다.</b> contentId 가 PK 라 이미 있으면 갱신, 없으면 삽입이다 —
     * poi_intro 처럼 기존 값을 읽어 계산할 것이 없어 native upsert 를 쓸 이유가 없다.
     */
    @Override
    @Transactional
    public int upsertAll(Collection<FestivalPeriod> periods) {
        if (periods.isEmpty()) {
            return 0;
        }
        return jpaRepository.saveAll(periods).size();
    }
}
