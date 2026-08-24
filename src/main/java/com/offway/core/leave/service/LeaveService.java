package com.offway.core.leave.service;

import com.offway.core.leave.domain.AvailableTime;
import com.offway.core.leave.domain.HolidayYear;
import com.offway.core.leave.domain.PeriodStyle;
import com.offway.core.leave.domain.SandwichHoliday;
import com.offway.core.leave.domain.TripPeriod;
import com.offway.core.leave.service.dto.AvailableTimeCommand;
import com.offway.core.leave.service.dto.AvailableTimeResult;
import com.offway.core.leave.service.dto.SandwichQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 연차·가용시간 유스케이스 조율.
 *
 * <p>공휴일 조회는 {@link HolidayProvider} 가 소유하고(적재된 달은 DB, 없는 달만 외부), 계산은
 * 도메인({@link AvailableTime})에 위임한다. 이 서비스 자체는 상태를 바꾸지 않아 {@code @Transactional} 이 없다 —
 * 조회 한 번과 순수 계산뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final HolidayProvider holidayProvider;

    /** 공휴일 폴백 캐시를 비운다 — 운영상 강제 갱신(고시 정정 등), 그리고 공유 컨텍스트 통합 테스트의 격리용. */
    public void evictCache() {
        holidayProvider.evictCache();
    }

    /**
     * 가용 정보(LNT)를 산출한다. 날짜를 직접 받았으면 그대로, 기간스타일을 받았으면 <b>가장 가까운 실제 구간으로 해석한 뒤</b>
     * 계산한다 — 어느 쪽이든 확정된 날짜 하나로 합류하므로 계산은 같다(결정 #38).
     */
    public AvailableTimeResult calculate(AvailableTimeCommand command) {
        Resolved resolved = resolvePeriod(command);
        TripPeriod period = resolved.period();
        AvailableTime availableTime = AvailableTime.of(
                period.startDate(),
                period.endDate(),
                resolved.holidays(),
                command.startDayLeave());
        log.debug(
                "가용시간 산출 {}~{} travelDays={} consumedLeave={} maxReachMin={}",
                period.startDate(),
                period.endDate(),
                availableTime.travelDays(),
                availableTime.consumedLeaveDays(),
                availableTime.maxReachMinutes());
        return new AvailableTimeResult(period, availableTime, command.startDayLeave());
    }

    /**
     * 한 해의 공휴일을 날짜 오름차순으로 돌려준다(#317).
     *
     * <p><b>앱의 로컬 계산을 서버와 같은 답으로 만들려는 것이다.</b> 앱은 가용시간 호출이 실패하면 자체
     * 계산으로 폴백하는데, 주말만 걸러 공휴일이 낀 주의 차감일을 실제보다 많게 냈다. 목록을 한 번 받아 두면
     * 폴백도 정확해지고, 날짜를 고칠 때마다 서버를 왕복하지 않아도 된다.
     *
     * <p>조회 자체는 {@code holidaysWithin} 이 그대로 한다 — DB 를 한 번에 읽고, 없는 달만 외부로 넘어가며,
     * 그 외부가 실패하면 빈 집합이 아니라 502 로 답한다. 공휴일을 "없음" 으로 넘기면 연차가 과다 계산되기
     * 때문인데, 그 규칙은 이 API 에도 그대로 필요하다.
     *
     * <p>연도 범위는 {@link HolidayYear} 가 막는다 — 아무 해나 받으면 요청 하나가 외부 호출 열두 번이 된다.
     */
    public List<LocalDate> holidaysOf(HolidayYear year) {
        return holidaysWithin(year.start(), year.end()).stream().sorted().toList();
    }

    /**
     * 조회 기간 안의 샌드위치 연휴를 찾아 <b>추천할 가치가 있는 황금 연차</b>만 돌려준다.
     *
     * <p>탐지·판정은 도메인({@link SandwichHoliday})이 소유하고, 서비스는 공휴일 조회(외부)와 필터(황금 여부·남은 연차)를 조율한다.
     */
    public List<SandwichHoliday> findSandwiches(SandwichQuery query) {
        Set<LocalDate> holidays = holidaysWithin(query.fromDate(), query.toDate());
        List<SandwichHoliday> detected = SandwichHoliday.detectWithin(query.fromDate(), query.toDate(), holidays);
        List<SandwichHoliday> recommended = detected.stream()
                .filter(SandwichHoliday::isGolden)
                .filter(sandwich -> query.withinRemainingLeave(sandwich.leaveDays()))
                .toList();
        log.debug("샌드위치 탐지 detected={} recommended={}", detected.size(), recommended.size());
        return recommended;
    }

    /**
     * 커맨드를 확정된 구간으로 바꾼다. 스타일 모드에서 <b>공휴일을 한 번만 조회</b>하는 것이 이 메서드의 요점이다 —
     * 해석에 쓴 창이 해석 결과를 덮으므로({@link PeriodStyle#MAX_RESOLVE_OFFSET_DAYS}), 같은 집합을 계산에 재사용해
     * 외부 호출을 두 번 하지 않는다.
     */
    private Resolved resolvePeriod(AvailableTimeCommand command) {
        return switch (command) {
            case AvailableTimeCommand.FixedDates fixed -> new Resolved(
                    new TripPeriod(fixed.startDate(), fixed.endDate()),
                    holidaysWithin(fixed.startDate(), fixed.endDate()));
            case AvailableTimeCommand.FromStyle fromStyle -> {
                LocalDate baseDate = fromStyle.baseDate();
                Set<LocalDate> holidays =
                        holidaysWithin(baseDate, baseDate.plusDays(PeriodStyle.MAX_RESOLVE_OFFSET_DAYS));
                yield new Resolved(
                        fromStyle.style().resolveFrom(baseDate, fromStyle.options(), holidays), holidays);
            }
        };
    }

    /** 확정된 구간과, 그 구간을 덮는 공휴일 집합. 조회를 한 번만 하려고 둘을 함께 들고 나른다. */
    private record Resolved(TripPeriod period, Set<LocalDate> holidays) {}

    /** 구간이 걸치는 각 월의 공휴일 합집합. 조회는 {@link HolidayProvider}(DB 우선)가 소유한다. */
    private Set<LocalDate> holidaysWithin(LocalDate start, LocalDate end) {
        return holidayProvider.holidaysWithin(start, end);
    }
}
