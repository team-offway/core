package com.offway.core.leave.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.leave.domain.HolidayException;
import com.offway.core.leave.domain.StoredHolidayMonth;
import com.offway.core.leave.infrastructure.holiday.HolidayClient;
import com.offway.core.leave.repository.HolidayMonthRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 공휴일 조회 — <b>DB 우선, 없는 달만 외부</b>(#193 3단계).
 *
 * <p>예전에는 인메모리 캐시가 전부라 배포할 때마다 처음부터 다시 물었다. 공휴일은 이 시스템에서 가장 정적인
 * 데이터인데(연 단위로 미리 공표되고 확정되면 안 바뀐다) 배포 횟수만큼 한도를 태우고 있었다.
 * {@link HolidayRefreshService} 가 하루 한 번 채우고, 이 클래스는 채워진 것을 읽는다.
 *
 * <p><b>외부 폴백을 남겨 둔 이유.</b> 적재 범위 밖(먼 미래 날짜로 계산)이나 첫 적재 전 짧은 창에는 DB 에 그
 * 달이 없다. 그때 "공휴일 없음" 으로 넘기면 공휴일이 평일로 세어져 소모 연차가 과다 계산된다 — 조용히 틀리는
 * 쪽이라 요청 경로에서 외부를 한 번 무는 편이 낫다. 정상 운영에서는 배치가 이미 채워 뒀으므로 타지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HolidayProvider {

    /**
     * 폴백 캐시 TTL — 연 단위로 미리 공표되는 데이터라 길게 잡아도 신선도 손해가 없다. 미공표 미래 연도가
     * 빈 결과로 오는 함정 때문에 무한히 두지는 않는다(공표되면 하루 안에 반영).
     */
    private static final Duration HOLIDAY_CACHE_TTL = Duration.ofHours(24);

    /** 실패 캐시 TTL — 외부가 죽어 있는 동안 매 요청이 read-timeout 을 다시 물지 않게 짧게 눌러둔다. */
    private static final Duration HOLIDAY_FAILURE_TTL = Duration.ofMinutes(5);

    /**
     * 폴백 캐시가 보관할 월 수. 키 공간이 <b>유한</b>하다 — 샌드위치 조회 상한(12개월)과 스타일 해석 창이
     * 묶어주므로 한 해 남짓이 전부다. 연도가 넘어가며 조금씩 늘 수 있어 몇 해분 여유를 둔다.
     */
    private static final int HOLIDAY_CACHE_MAX_MONTHS = 64;

    /**
     * 빈 달에 동시 요청이 몰렸을 때 첫 적재를 기다릴 상한. loader 가 특일정보 <b>단일 호출</b>(timeout 6초)이라
     * 여유 1초를 얹었다. 기다리는 쪽은 적재하는 쪽보다 늦게 끝나지 않는다.
     */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(7);

    private final HolidayClient holidayClient;
    private final HolidayMonthRepository holidayMonthRepository;

    /** 적재 범위 밖 달에만 쓰는 폴백 캐시. */
    private final ExternalDataCache<YearMonth, HolidayLookup> holidayCache =
            new ExternalDataCache<>(HOLIDAY_CACHE_MAX_MONTHS, FIRST_LOAD_WAIT);

    /** 폴백 캐시를 비운다 — 운영상 강제 갱신(고시 정정 등), 그리고 공유 컨텍스트 통합 테스트의 격리용. */
    public void evictCache() {
        holidayCache.evictAll();
    }

    /**
     * 구간이 걸치는 각 달의 공휴일을 모아 합집합으로 돌려준다. 구간 밖 날짜는 도메인이 알아서 무시한다.
     *
     * <p>DB 는 <b>달 목록 전체를 한 번에</b> 읽는다 — 달마다 물으면 3개월 구간이 질의 3번이 된다.
     */
    public Set<LocalDate> holidaysWithin(LocalDate start, LocalDate end) {
        List<YearMonth> months = monthsBetween(start, end);
        Map<YearMonth, StoredHolidayMonth> stored = holidayMonthRepository.findByMonths(months).stream()
                .collect(Collectors.toMap(StoredHolidayMonth::month, Function.identity()));

        Set<LocalDate> holidays = new HashSet<>();
        for (YearMonth month : months) {
            StoredHolidayMonth found = stored.get(month);
            holidays.addAll(found != null ? found.holidays() : fromExternal(month));
        }
        return holidays;
    }

    /** 구간이 걸치는 달을 오름차순으로. */
    private static List<YearMonth> monthsBetween(LocalDate start, LocalDate end) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth month = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!month.isAfter(last)) {
            months.add(month);
            month = month.plusMonths(1);
        }
        return months;
    }

    /**
     * 적재되지 않은 달 — 캐시(single-flight)를 거쳐 외부에 묻는다.
     *
     * <p><b>빈 결과를 실패로 보지 않는다.</b> 관광빅데이터와 다르다 — 공휴일이 아예 없는 달은 정상이고
     * 흔하다(4·6·11월). 대신 미공표 미래 연도가 빈 결과로 오는 함정이 있는데, TTL 이 하루라 공표되면
     * 하루 안에 반영된다.
     */
    private Set<LocalDate> fromExternal(YearMonth month) {
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
     * <p>{@link ExternalDataCache} 의 loader 는 예외를 던지면 안 된다(프리미티브가 삼켜 폴백으로 degrade 한다).
     * 그런데 공휴일은 부가 정보가 아니라 연차 계산의 <b>재료</b>다 — 조용히 빈 집합으로 넘기면 공휴일이 평일로
     * 세어져 소모 연차가 과다 계산된다. 그래서 실패를 값으로 캐시하고 캐시 밖에서 다시 던져 502 계약을 지킨다.
     *
     * <p>덤으로 실패도 짧게 캐시되므로, 외부가 죽어 있는 동안 매 요청이 read-timeout 을 다시 물지 않고 즉시
     * 같은 실패를 받는다.
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
