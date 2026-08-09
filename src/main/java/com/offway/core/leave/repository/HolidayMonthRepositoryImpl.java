package com.offway.core.leave.repository;

import com.offway.core.leave.domain.StoredHolidayMonth;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class HolidayMonthRepositoryImpl implements HolidayMonthRepository {

    private final HolidayMonthJpaRepository jpaRepository;

    @Override
    public List<StoredHolidayMonth> findByMonths(List<YearMonth> months) {
        if (months.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByBaseYmIn(
                months.stream().map(StoredHolidayMonth::baseYmOf).toList());
    }

    /**
     * 한 트랜잭션 안에서 해당 달만 비우고 새로 넣는다 — 중간 상태(그 달이 통째로 빈 구간)가 조회에 보이지 않게.
     *
     * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약).
     */
    @Override
    @Transactional
    public void replaceMonths(List<StoredHolidayMonth> months) {
        if (months.isEmpty()) {
            return;
        }
        jpaRepository.deleteByBaseYmIn(
                months.stream().map(StoredHolidayMonth::getBaseYm).toList());
        jpaRepository.saveAll(months);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
