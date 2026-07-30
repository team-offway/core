package com.offway.core.leave.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.leave.domain.AvailableTime;
import com.offway.core.leave.domain.HolidayException;
import com.offway.core.leave.domain.PeriodStyle;
import com.offway.core.leave.domain.SandwichHoliday;
import com.offway.core.leave.domain.TripPeriod;
import com.offway.core.leave.infrastructure.holiday.HolidayClient;
import com.offway.core.leave.service.dto.AvailableTimeCommand;
import com.offway.core.leave.service.dto.AvailableTimeResult;
import com.offway.core.leave.service.dto.SandwichQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
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

    /**
     * 공휴일 캐시 TTL — 연 단위로 미리 공표되는 데이터라 길게 잡아도 신선도 손해가 없다. 미공표 미래 연도가 빈 결과로 오는
     * 함정 때문에 무한히 두지는 않는다(공표되면 하루 안에 반영).
     */
    private static final Duration HOLIDAY_CACHE_TTL = Duration.ofHours(24);

    /** 실패 캐시 TTL — 외부가 죽어 있는 동안 매 요청이 read-timeout 을 다시 물지 않게 짧게 눌러둔다. */
    private static final Duration HOLIDAY_FAILURE_TTL = Duration.ofMinutes(5);

    private final HolidayClient holidayClient;

    /**
     * 보관할 월 수. 키 공간이 <b>유한</b>하다 — 샌드위치 조회 상한(12개월)과 스타일 해석 창이 묶어주므로 한 해 남짓이
     * 전부다. 연도가 넘어가며 조금씩 늘 수 있어 몇 해분 여유를 둔다.
     */
    private static final int HOLIDAY_CACHE_MAX_MONTHS = 64;

    /** 월별 공휴일 캐시. */
    private final ExternalDataCache<YearMonth, HolidayLookup> holidayCache =
            new ExternalDataCache<>(HOLIDAY_CACHE_MAX_MONTHS);

    /** 공휴일 캐시를 비운다 — 운영상 강제 갱신(고시 정정 등), 그리고 공유 컨텍스트 통합 테스트의 격리용. */
    public void evictCache() {
        holidayCache.evictAll();
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
                command.transport(),
                command.halfDayStart());
        log.info(
                "가용시간 산출 {}~{} travelDays={} consumedLeave={} maxReachMin={}",
                period.startDate(),
                period.endDate(),
                availableTime.travelDays(),
                availableTime.consumedLeaveDays(),
                availableTime.maxReachMinutes());
        return new AvailableTimeResult(period, availableTime);
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
        log.info("샌드위치 탐지 detected={} recommended={}", detected.size(), recommended.size());
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

    /** 구간이 걸치는 각 월의 공휴일을 모아 합집합으로 돌려준다. 구간 밖 날짜는 도메인이 알아서 무시한다. */
    private Set<LocalDate> holidaysWithin(LocalDate start, LocalDate end) {
        Set<LocalDate> holidays = new HashSet<>();
        YearMonth month = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!month.isAfter(last)) {
            holidays.addAll(holidaysOf(month));
            month = month.plusMonths(1);
        }
        return holidays;
    }

    /**
     * 한 달의 공휴일 — 캐시(single-flight) 우선. 공휴일은 연 단위로 미리 공표되는, 이 시스템에서 가장 정적인 데이터라
     * 요청마다 외부를 부를 이유가 없다(성능 규약 "요청 경로에서 외부 I/O 를 뺀다").
     *
     * <p><b>빈 결과를 실패로 보지 않는다.</b> 관광빅데이터와 다르다 — 공휴일이 아예 없는 달은 정상이고 흔하다(4·6·11월).
     * 대신 미공표 미래 연도가 빈 결과로 오는 함정이 있는데, TTL 이 하루라 공표되면 하루 안에 반영된다.
     */
    private Set<LocalDate> holidaysOf(YearMonth month) {
        HolidayLookup lookup = holidayCache.get(month, (key, stale) -> {
            try {
                Set<LocalDate> fresh = holidayClient.getHolidays(key.getYear(), key.getMonthValue());
                return new Loaded<>(HolidayLookup.of(fresh), HOLIDAY_CACHE_TTL);
            } catch (HolidayException e) {
                // 마지막 성공값이 있으면 그걸 쓴다 — 공휴일은 거의 변하지 않아 stale 이 실패보다 훨씬 정확하다.
                if (stale != null && !stale.failed()) {
                    log.warn("공휴일 조회 실패 — 마지막 성공값 재사용 month={}", key, e);
                    return new Loaded<>(stale, HOLIDAY_FAILURE_TTL);
                }
                log.warn("공휴일 조회 실패 — 폴백할 성공값이 없어 실패로 응답합니다 month={}", key, e);
                return new Loaded<>(HolidayLookup.failure(), HOLIDAY_FAILURE_TTL);
            }
        }, HolidayLookup.failure());

        if (lookup.failed()) {
            throw HolidayException.lookupFailedRecently();
        }
        return lookup.holidays();
    }

    /**
     * 공휴일 조회 결과 — <b>실패를 값으로</b> 들고 캐시에 넣는다.
     *
     * <p>{@link ExternalDataCache} 의 loader 는 예외를 던지면 안 된다(프리미티브가 삼켜 폴백으로 degrade 한다). 그런데
     * 공휴일은 부가 정보가 아니라 연차 계산의 <b>재료</b>다 — 조용히 빈 집합으로 넘기면 공휴일이 평일로 세어져 소모 연차가
     * 과다 계산된다. 그래서 실패를 값으로 캐시하고 캐시 밖에서 다시 던져 502 계약을 지킨다.
     *
     * <p>덤으로 실패도 짧게 캐시되므로, 외부가 죽어 있는 동안 매 요청이 read-timeout 을 다시 물지 않고 즉시 같은 실패를 받는다.
     *
     * @param holidays 조회된 공휴일 (실패면 빈 집합 — 쓰이지 않는다)
     * @param failed 조회가 실패했는가
     */
    private record HolidayLookup(Set<LocalDate> holidays, boolean failed) {

        static HolidayLookup of(Set<LocalDate> holidays) {
            return new HolidayLookup(Set.copyOf(holidays), false);
        }

        /** 이름이 {@code failed()} 가 아닌 이유 — 같은 이름의 record 접근자와 충돌한다. */
        static HolidayLookup failure() {
            return new HolidayLookup(Set.of(), true);
        }
    }
}
