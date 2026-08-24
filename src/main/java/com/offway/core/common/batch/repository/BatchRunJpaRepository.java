package com.offway.core.common.batch.repository;

import com.offway.core.common.batch.domain.BatchRun;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data — {@link BatchRunRepositoryImpl} 이 위임한다. */
public interface BatchRunJpaRepository extends JpaRepository<BatchRun, Long> {

    Optional<BatchRun> findByName(String name);

    /**
     * 그 날짜 밖의 기록을 가진 행만 <b>조건부로</b> 갱신한다 — 선점의 원자성이 여기서 나온다(#314).
     *
     * <p>읽고 나서 쓰면 그 사이에 다른 실행이 같은 값을 읽어 <b>둘 다</b> 돈다. 한 문장으로 두면 DB 가 행
     * 잠금으로 갈라 준다.
     *
     * <p>날짜 비교를 함수 대신 <b>구간</b>으로 하는 이유는 인덱스와 방언 때문이다 — {@code DATE(last_run_at)}
     * 는 컬럼을 감싸 인덱스를 못 타고 DB 마다 함수 이름이 다르다.
     *
     * <p><b>메서드에 직접 트랜잭션을 연다.</b> {@code @Modifying} 질의는 트랜잭션 없이는 못 돌고, 그렇다고
     * 호출부({@code tryStartOn})를 통째로 감싸면 뒤따르는 INSERT 경합 실패가 그 트랜잭션을 롤백 상태로
     * 만들어 이후 판단을 못 한다. 두 단계가 각자 자기 경계를 갖게 둔다.
     *
     * @param dayStart 그 날짜 00:00
     * @param nextDayStart 다음 날 00:00 (미포함)
     * @return 갱신된 행 수. 1 이면 이번 호출이 선점했다
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update BatchRun b set b.lastRunAt = :at
            where b.name = :name and (b.lastRunAt < :dayStart or b.lastRunAt >= :nextDayStart)
            """)
    int claimIfNotRunOn(
            @Param("name") String name,
            @Param("at") LocalDateTime at,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("nextDayStart") LocalDateTime nextDayStart);
}
