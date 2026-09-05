package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionDailyTourists;
import com.offway.core.trip.domain.RegionVisitorDaily;
import com.offway.core.trip.domain.VisitorType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * port 구현(adapter) — Spring Data 에 위임.
 *
 * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약). 여기는 짧은 트랜잭션으로 쓰기만 한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RegionVisitorDailyRepositoryImpl implements RegionVisitorDailyRepository {

    private final RegionVisitorDailyJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RegionVisitorDaily> findBetween(Collection<String> signguCodes, LocalDate from, LocalDate to) {
        if (signguCodes.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findBySignguCodeInAndBaseDateBetween(signguCodes, from, to);
    }

    /**
     * 관광객으로 셀 유형을 <b>도메인에서 가져와</b> 넘긴다. 쿼리에 {@code <> LOCAL} 을 박지 않는 이유는
     * {@link VisitorType#isTourist()} 하나만 정본으로 두기 위해서다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RegionDailyTourists> sumTouristsByDate(LocalDate from, LocalDate to) {
        List<VisitorType> touristTypes = Arrays.stream(VisitorType.values())
                .filter(VisitorType::isTourist)
                .toList();
        return jpaRepository.sumTouristsByRegionAndDate(from, to, touristTypes);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasMonth(YearMonth month) {
        return jpaRepository.existsByBaseDateBetween(month.atDay(1), month.atEndOfMonth());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> latestDate() {
        return Optional.ofNullable(jpaRepository.findMaxBaseDate());
    }

    /**
     * <b>UNIQUE 제약이 중복을 막는다.</b> 미리 조회해 거르지 않는 이유는 두 가지다 — 확인과 저장 사이에
     * 다른 실행이 끼어들 수 있고, 한 달치를 미리 읽어 비교하면 그 자체가 수천 행 조회다.
     *
     * <p>충돌하면 <b>그 묶음만 건너뛰고 계속한다.</b> 전부 버리면 이미 받아 온 다른 달까지 잃는다.
     */
    @Override
    @Transactional
    public int insertIfAbsent(Collection<RegionVisitorDaily> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        try {
            return jpaRepository.saveAll(rows).size();
        } catch (DataIntegrityViolationException e) {
            // 같은 달을 두 번 넣으려 한 것이다. 값이 불변이라 이미 있는 쪽이 옳다.
            log.info("이미 받아 둔 방문자 행이 있어 건너뜁니다 rows={}", rows.size());
            return 0;
        }
    }
}
