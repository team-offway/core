package com.offway.core.leave.repository;

import com.offway.core.leave.domain.StoredHolidayMonth;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data — {@link HolidayMonthRepositoryImpl} 이 위임한다. */
public interface HolidayMonthJpaRepository extends JpaRepository<StoredHolidayMonth, Long> {

    List<StoredHolidayMonth> findByBaseYmIn(Collection<String> baseYms);

    /**
     * 파생 {@code deleteBy...} 대신 벌크 삭제를 쓴다.
     *
     * <p>파생 삭제는 엔티티를 읽어 {@code em.remove} 를 호출할 뿐이라 실제 DELETE 가 커밋 시점의 액션 큐로
     * 밀린다. Hibernate 는 그 큐에서 INSERT 를 DELETE 보다 <b>먼저</b> 흘리므로, 같은 달을 지우고 다시 넣는
     * 교체가 {@code uk_holiday_month_base_ym} 위반으로 터진다. 벌크 DELETE 는 즉시 실행돼 그 순서 문제가 없다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StoredHolidayMonth h where h.baseYm in :baseYms")
    void deleteByBaseYmIn(@Param("baseYms") Collection<String> baseYms);
}
