package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.service.dto.SlotHours;
import com.offway.core.leave.service.HolidayProvider;
import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.domain.OpeningStatus;
import com.offway.core.trip.repository.PoiIntroRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 코스에 실린 장소들의 운영시간을 <b>DB 에서만</b> 읽고, 여행일이 오늘이면 <b>지금 여는지</b>까지 판정한다
 * (#157·#189).
 *
 * <p>요청 경로에서 외부를 부르지 않는다. 슬롯마다 {@code detailIntro2} 를 부르면 코스 하나에 20건이 넘어
 * 관광정보 한도가 코스 40개면 마른다. 받아 두는 일은 배치({@code PoiIntroRefreshService})가 한다.
 *
 * <p><b>판정은 오늘 여행 중일 때만.</b> 지금 시각으로 내리는 판정이라 다음 주 코스에 붙이면 사용자가
 * 여행일 상태로 읽는다 — 없는 것보다 나쁘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpeningHoursProvider {

    /** "오늘" 판정은 KST — 사용자가 서 있는 시간대다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 공휴일 조회 실패를 다시 남기기까지의 간격.
     *
     * <p>{@code HolidayProvider} 가 실패를 5분간 캐시한다 — 그 창 안의 요청은 <b>같은 실패</b>를 즉시
     * 돌려받으므로 한 줄이면 사실을 다 담는다. 창이 지나면 외부를 다시 물으니 그때는 새 실패고, 다시 남긴다.
     */
    private static final Duration HOLIDAY_WARN_INTERVAL = Duration.ofMinutes(5);

    private final PoiIntroRepository poiIntroRepository;
    private final HolidayProvider holidayProvider;

    /** 마지막으로 degrade 를 남긴 시각. 요청이 동시에 몰려도 한 줄만 나가게 CAS 로 집는다. */
    private final AtomicReference<Instant> lastHolidayWarn = new AtomicReference<>();

    /**
     * 코스 전체 슬롯의 운영 정보를 한 번의 조회로 가져온다 — 슬롯마다 읽으면 N+1 이 된다.
     *
     * <p>공휴일 조회도 <b>코스당 한 번</b>이다. 슬롯마다 물으면 같은 날짜를 스무 번 묻는다.
     */
    public Map<String, SlotHours> forCourse(Course course) {
        List<String> contentIds = course.getDays().stream()
                .map(DaySchedule::getSlots)
                .flatMap(List::stream)
                .map(Slot::getPoiContentId)
                .filter(Objects::nonNull) // 교통 거점 칸은 장소가 아니라 운영시간이 없다(#415)
                .distinct()
                .toList();
        Map<String, OpeningHours> stored = poiIntroRepository.findByContentIds(contentIds);
        if (stored.isEmpty()) {
            return Map.of();
        }

        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        boolean judge = course.covers(now.toLocalDate());
        boolean holiday = judge && isHoliday(now.toLocalDate());

        Map<String, SlotHours> result = new HashMap<>();
        stored.forEach((contentId, hours) ->
                result.put(contentId, new SlotHours(hours,
                        judge ? hours.statusAt(now, holiday) : OpeningStatus.UNKNOWN)));
        return result;
    }

    /**
     * 공휴일 조회가 실패해도 코스는 나가야 한다 — 예외 조항 판정만 보수적으로 간다(휴무로 본다).
     *
     * <p><b>degrade 했으면 왜 했는지 남긴다.</b> 안 남기면 공휴일 월요일에 "오늘 휴무" 가 나가도 아무도
     * 모른다. 다만 코스 조회마다 찍으면 장애 동안 로그가 요청 수만큼 불어나므로
     * {@link #HOLIDAY_WARN_INTERVAL} 로 눌러 둔다.
     *
     * <p>예외는 타입만 적는다 — 스택·메시지는 이미 {@code HolidayProvider} 가 실패 지점에서 남겼고,
     * 여기서 또 풀면 같은 장애가 두 번 쌓인다.
     */
    private boolean isHoliday(LocalDate today) {
        try {
            return holidayProvider.holidaysWithin(today, today).contains(today);
        } catch (RuntimeException e) {
            warnDegraded(e);
            return false;
        }
    }

    /** 같은 실패 창에서 한 줄만 남긴다. 창이 지나면 다시 남겨 장애가 계속되는 것도 보이게 한다. */
    private void warnDegraded(RuntimeException cause) {
        Instant now = Instant.now();
        Instant last = lastHolidayWarn.get();
        if (last != null && now.isBefore(last.plus(HOLIDAY_WARN_INTERVAL))) {
            return;
        }
        if (!lastHolidayWarn.compareAndSet(last, now)) {
            return;
        }
        log.warn("공휴일 조회 실패 — 공휴일 정상운영 예외를 적용하지 않고 판정합니다 cause={}",
                cause.getClass().getSimpleName());
    }
}
