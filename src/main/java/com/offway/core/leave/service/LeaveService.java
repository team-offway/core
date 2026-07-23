package com.offway.core.leave.service;

import com.offway.core.leave.domain.AvailableTime;
import com.offway.core.leave.infrastructure.holiday.HolidayClient;
import com.offway.core.leave.service.dto.CreateAvailableTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 연차·가용시간 유스케이스 조율.
 *
 * <p>외부 호출(특일정보)을 트랜잭션 밖에서 끝내고 계산은 도메인({@link AvailableTime})에 위임한다. 이 서비스는 DB 를 만지지 않아
 * {@code @Transactional} 이 없다 — 상태 없는 계산 + 외부 조회뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final HolidayClient holidayClient;

    /** 확정된 날짜 구간으로 가용 정보(LNT)를 산출한다. */
    public AvailableTime calculate(CreateAvailableTime command) {
        Set<LocalDate> holidays = holidaysWithin(command.startDate(), command.endDate());
        AvailableTime availableTime = AvailableTime.of(
                command.startDate(), command.endDate(), holidays, command.transport(), command.halfDayStart());
        log.info(
                "가용시간 산출 travelDays={} consumedLeave={} maxReachMin={}",
                availableTime.travelDays(),
                availableTime.consumedLeaveDays(),
                availableTime.maxReachMinutes());
        return availableTime;
    }

    /** 구간이 걸치는 각 월의 공휴일을 모아 합집합으로 돌려준다. 구간 밖 날짜는 도메인이 알아서 무시한다. */
    private Set<LocalDate> holidaysWithin(LocalDate start, LocalDate end) {
        Set<LocalDate> holidays = new HashSet<>();
        YearMonth month = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!month.isAfter(last)) {
            holidays.addAll(holidayClient.getHolidays(month.getYear(), month.getMonthValue()));
            month = month.plusMonths(1);
        }
        return holidays;
    }
}
