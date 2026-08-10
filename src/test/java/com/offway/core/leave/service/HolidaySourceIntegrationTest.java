package com.offway.core.leave.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.leave.domain.StoredHolidayMonth;
import com.offway.core.leave.repository.HolidayMonthRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공휴일을 <b>어디서 읽는가</b>(#193 3단계) — 적재분이 있으면 DB, 없으면 외부.
 *
 * <p><b>stub 을 두지 않는다.</b> 테스트 환경에는 특일정보 키가 없어 외부 폴백이 항상 빈 집합을 준다. 그래서
 * "값이 나왔다" 는 것 자체가 DB 를 읽었다는 증거다 — 호출 횟수를 세는 stub 없이도 갈린다. 덤으로
 * {@code @TestConfiguration} 이 없어 컨텍스트를 새로 만들지 않는다.
 *
 * <p>{@code @Transactional} 로 롤백한다. Testcontainers MySQL 은 테스트 JVM 하나를 통째로 공유하므로,
 * 여기서 넣은 {@code holiday_month} 행이 커밋되면 stub 으로 공휴일을 꾸미는 다른 테스트가 그 행을 읽는다.
 */
@SpringBootTest
@Transactional
class HolidaySourceIntegrationTest {

    private static final LocalDateTime STORED_AT = LocalDateTime.of(2026, 5, 1, 3, 0);
    private static final YearMonth MAY = YearMonth.of(2026, 5);
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final LocalDate CHILDRENS_DAY = LocalDate.of(2026, 5, 5);

    @Autowired
    private HolidayProvider holidayProvider;

    @Autowired
    private HolidayMonthRepository holidayMonthRepository;

    @Test
    void 적재된_달은_DB_공휴일을_쓴다() {
        holidayMonthRepository.replaceMonths(
                List.of(StoredHolidayMonth.of(MAY, Set.of(CHILDRENS_DAY), STORED_AT)));

        Set<LocalDate> holidays =
                holidayProvider.holidaysWithin(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertEquals(Set.of(CHILDRENS_DAY), holidays);
    }

    @Test
    void 공휴일이_없는_달도_행으로_남아_받아온_달로_구분된다() {
        // 달 단위 저장의 존재 이유다. 날짜만 쌓으면 "공휴일 없는 달"과 "안 받아온 달"이 똑같이 행 없음이 되고,
        // 뒤쪽을 앞쪽으로 오인하면 공휴일이 평일로 세어져 소모 연차가 과다 계산된다.
        YearMonth april = YearMonth.of(2026, 4);
        holidayMonthRepository.replaceMonths(List.of(StoredHolidayMonth.of(april, Set.of(), STORED_AT)));

        List<StoredHolidayMonth> found = holidayMonthRepository.findByMonths(List.of(april));

        assertEquals(1, found.size());
        assertTrue(found.getFirst().holidays().isEmpty());
    }

    @Test
    void 적재되지_않은_달은_결과에_들어오지_않는다() {
        // 호출자가 "받아온 적 없는 달"을 구분해야 하므로 빈 값으로 채워 주지 않는다.
        holidayMonthRepository.replaceMonths(
                List.of(StoredHolidayMonth.of(MAY, Set.of(CHILDRENS_DAY), STORED_AT)));

        List<StoredHolidayMonth> found = holidayMonthRepository.findByMonths(List.of(MAY, JUNE));

        assertEquals(List.of(MAY), found.stream().map(StoredHolidayMonth::month).toList());
    }

    @Test
    void 두_달에_걸친_구간은_달마다_갈라_읽는다() {
        // 5월은 적재 전이라 폴백(키 없음 → 빈 집합), 6월은 적재분. 한쪽만 봤다면 결과가 달라진다.
        LocalDate unionDay = LocalDate.of(2026, 6, 1);
        holidayMonthRepository.replaceMonths(List.of(StoredHolidayMonth.of(JUNE, Set.of(unionDay), STORED_AT)));

        Set<LocalDate> holidays =
                holidayProvider.holidaysWithin(LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 2));

        assertEquals(Set.of(unionDay), holidays);
    }

    @Test
    void 같은_달을_다시_적재하면_교체된다() {
        // 교체가 "지우고 넣기"라 Hibernate 액션 큐 순서(INSERT 가 DELETE 보다 먼저)에 걸리면 유니크 제약으로
        // 터진다. 벌크 삭제로 그 순서를 피한 것을 여기서 못 박는다.
        holidayMonthRepository.replaceMonths(
                List.of(StoredHolidayMonth.of(MAY, Set.of(CHILDRENS_DAY), STORED_AT)));

        LocalDate corrected = LocalDate.of(2026, 5, 25);
        holidayMonthRepository.replaceMonths(
                List.of(StoredHolidayMonth.of(MAY, Set.of(CHILDRENS_DAY, corrected), STORED_AT)));

        List<StoredHolidayMonth> found = holidayMonthRepository.findByMonths(List.of(MAY));
        assertEquals(1, found.size());
        assertEquals(Set.of(CHILDRENS_DAY, corrected), found.getFirst().holidays());
    }
}
