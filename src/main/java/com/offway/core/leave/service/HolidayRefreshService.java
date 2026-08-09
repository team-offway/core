package com.offway.core.leave.service;

import com.offway.core.leave.domain.HolidayException;
import com.offway.core.leave.domain.HolidayRefreshWindow;
import com.offway.core.leave.domain.StoredHolidayMonth;
import com.offway.core.leave.infrastructure.holiday.HolidayClient;
import com.offway.core.leave.repository.HolidayMonthRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 공휴일을 받아 DB 에 적재한다(#193 3단계).
 *
 * <p><b>왜 DB 인가.</b> 인메모리 캐시는 프로세스와 함께 죽어, 배포할 때마다 같은 달을 다시 물었다. 공휴일은
 * 연 단위로 미리 공표되고 확정되면 안 바뀌는, 이 시스템에서 가장 정적인 데이터다.
 *
 * <p><b>순차로 부른다.</b> 병렬로 던지면 초당 호출이 폭증해 429 를 맞는다(#191). 하루 한 번 도는 배경
 * 작업이라 십수 건을 순서대로 무는 시간은 아무도 기다리지 않는다.
 *
 * <p><b>테스트 컨텍스트에서는 뜨지 않는다</b>({@code @Profile}). 스케줄이 테스트 도중 발화하면 stub 응답이
 * 테스트 트랜잭션 <b>밖에서</b> DB 에 남아, 뒤 테스트가 자기 stub 대신 그 값을 읽는다 — 순서에 따라 결과가
 * 달라지는 종류의 실패다. 덮는 범위·재조회 판단은 {@link HolidayRefreshWindow} 가 들고 있어 단위 테스트로
 * 망라하고, 적재 흐름은 이 빈이 뜨는 프로파일에서 확인한다({@code HomeCacheWarmer} 와 같은 방식).
 */
@Slf4j
@Service
@Profile("local | prod")
@RequiredArgsConstructor
public class HolidayRefreshService {

    /** "오늘" 판정은 KST 기준 — 저장 시각도 같은 기준이라야 하루 한 번이 하루 한 번으로 동작한다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 부팅 후 첫 적재까지 지연 — 기동·헬스체크를 방해하지 않게. */
    private static final String INITIAL_DELAY = "PT75S";

    /** 갱신 주기. 이미 오늘 채웠으면 외부를 아예 안 부르므로, 재배포가 잦아도 호출량이 늘지 않는다. */
    private static final String REFRESH_INTERVAL = "P1D";

    private final HolidayClient holidayClient;
    private final HolidayMonthRepository holidayMonthRepository;

    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = REFRESH_INTERVAL)
    public void refresh() {
        HolidayRefreshWindow window = HolidayRefreshWindow.of(LocalDate.now(SERVICE_ZONE));
        List<YearMonth> targets = window.targetMonths();
        Map<YearMonth, StoredHolidayMonth> stored = holidayMonthRepository.findByMonths(targets).stream()
                .collect(Collectors.toMap(StoredHolidayMonth::month, Function.identity()));

        List<YearMonth> due = targets.stream()
                .filter(month -> window.needsRefresh(month, stored.get(month)))
                .toList();
        if (due.isEmpty()) {
            log.info("공휴일이 이미 최신({})이라 갱신을 건너뜁니다 — 저장 {}개월", window.today(), stored.size());
            return;
        }

        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        List<StoredHolidayMonth> fetched = new ArrayList<>();
        int failed = 0;
        for (YearMonth month : due) {
            try {
                Set<LocalDate> holidays = holidayClient.getHolidays(month.getYear(), month.getMonthValue());
                fetched.add(StoredHolidayMonth.of(month, holidays, now));
            } catch (HolidayException e) {
                // 달마다 격리한다 — 한 달이 실패해도 나머지는 채운다. 실패한 달은 저장하지 않아 이전 값이 남고,
                // 이전 값도 없으면 조회 시 폴백이 받는다.
                failed++;
                log.warn("공휴일 갱신 실패 — 이전 값을 유지합니다 month={} cause={}",
                        month, e.getClass().getSimpleName());
            }
        }

        if (fetched.isEmpty()) {
            log.warn("공휴일 갱신이 한 건도 성공하지 못했습니다 — 이전 적재를 유지합니다(대상 {}개월·저장 {}개월)",
                    due.size(), holidayMonthRepository.count());
            return;
        }
        if (fetched.stream().allMatch(month -> month.holidays().isEmpty())) {
            // 공휴일이 하나도 없는 달은 흔하지만, 열 달 넘게 전부 비는 일은 없다. 키가 없거나 외부가 성공
            // 코드로 빈 응답을 주는 상태에 가깝다 — 그걸 적재하면 "공휴일 없음" 이 사실로 굳어 소모 연차가
            // 과다 계산된다. 덮지 않고 남긴다.
            log.warn("공휴일 갱신 결과가 전부 비어 적재를 건너뜁니다 — 대상 {}개월·실패 {}건(저장 {}개월 유지)",
                    due.size(), failed, holidayMonthRepository.count());
            return;
        }
        holidayMonthRepository.replaceMonths(fetched);

        int holidayCount =
                fetched.stream().mapToInt(month -> month.holidays().size()).sum();
        if (failed > 0) {
            log.warn("공휴일 적재 완료 {}/{}개월 공휴일={}건 — 실패 {}건은 이전 값 유지",
                    fetched.size(), due.size(), holidayCount, failed);
            return;
        }
        log.info("공휴일 적재 완료 {}개월 공휴일={}건 (대상 {}개월 중 {}개월은 이미 최신)",
                fetched.size(), holidayCount, targets.size(), targets.size() - due.size());
    }
}
