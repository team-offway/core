package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.trip.domain.FestivalPlace;
import com.offway.core.trip.repository.FestivalPlaceRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * "이 축제 기간에 가보는 건 어떠냐" 를 말할 재료(#622).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>코스를 먼저 보고 날짜를 정하는 사용자가 있다. 그에게 <b>날짜를 권할 근거</b>가 지금은 없다 —
 * 후보를 고르는 자리가 여행일에 열리는 축제만 남기고 나머지를 버리기 때문이다({@code RegionPoiService}).
 *
 * <p>버리는 것들 중에 값어치가 있다. 실측(2026-09-29)에서 표준데이터에 앞으로 열릴 축제가 58건,
 * 그중 10월 시작이 45건 · 30지역이다. "10월 2일부터 여기서 축제를 한다" 는 그 자체가 갈 이유가 된다.
 *
 * <h2>코스에 실린 축제는 빼낸다</h2>
 *
 * <p>같은 것을 두 번 말하지 않는다. 그날 열리는 축제는 이미 슬롯으로 들어가 기간까지 붙으므로
 * ({@code FestivalPeriodProvider}), 여기서 또 권하면 "지금 가는 중인 축제에 가보라" 가 된다.
 *
 * <p>질의가 <b>아직 시작하지 않은 축제</b>만 보는 것도 같은 이유다. 진행 중인 축제는 코스 쪽의 몫이다.
 *
 * <h2>비용</h2>
 *
 * <p>코스당 <b>DB 질의 한 번</b>이다. 외부 호출이 없고, 상한({@link #MAX_SUGGESTIONS})이 있어 응답
 * 크기가 코스 길이와 무관하게 고정된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FestivalSuggestionProvider {

    /**
     * 몇 건까지 권하나.
     *
     * <p>3 이다. 앱이 한 줄짜리 배너로 보여주는 자리라 그보다 많으면 스크롤이 생기고, 스크롤이 생기면
     * 아무도 안 읽는다. 가까운 것부터 오므로 잘려 나가는 쪽은 먼 미래다.
     */
    private static final int MAX_SUGGESTIONS = 3;

    private final FestivalPlaceRepository festivalPlaceRepository;

    /** 서비스 기준 시간대 — 여행일이 없을 때의 "오늘" 을 여기서 읽는다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public List<FestivalPlace> forCourse(Course course) {
        return forCourse(course, LocalDate.now(SERVICE_ZONE));
    }

    /**
     * 이 코스의 지역에서 <b>여행일 뒤에</b> 열릴 축제.
     *
     * <p>여행일을 모르면 오늘을 기준으로 삼는다. "날짜를 안 정했다" 는 이 기능이 가장 값어치 있는
     * 경우라, 기준이 없다고 비워 보내면 필요한 자리에서만 빠진다.
     *
     * <p><b>오늘을 밖에서 넣을 수 있게 연다</b> — 테스트가 시계에 기대지 않게 하려는 것이고,
     * {@code AttractionCrowdRefreshService.refresh(LocalDate)} 와 같은 자리다.
     *
     * @return 가까운 것부터 최대 {@link #MAX_SUGGESTIONS} 건. 없으면 빈 목록
     */
    public List<FestivalPlace> forCourse(Course course, LocalDate today) {
        LocalDate from = course.getTravelDate() == null ? today : course.getTravelDate();
        // 상한만큼 받으면 코스에 실린 축제를 걸러낸 뒤 모자랄 수 있다. 걸러낼 수 있는 최대치가
        // 코스에 실린 축제 수이므로 그만큼 더 받는다 — 지금 상한은 1이지만 상수를 따라간다.
        List<String> onCourse = festivalIdsOn(course);
        List<FestivalPlace> upcoming =
                festivalPlaceRepository.findUpcomingAfter(
                        course.getRegionId(), from, MAX_SUGGESTIONS + onCourse.size());
        if (upcoming.isEmpty()) {
            return List.of();
        }

        Set<String> exclude = Set.copyOf(onCourse);
        List<FestivalPlace> suggestions = upcoming.stream()
                .filter(festival -> !exclude.contains(festival.publicId()))
                .limit(MAX_SUGGESTIONS)
                .toList();
        if (!suggestions.isEmpty()) {
            log.debug("축제 제안 regionId={} 제안={}건 코스에실림={}건",
                    course.getRegionId(), suggestions.size(), onCourse.size());
        }
        return suggestions;
    }

    /** 이 코스가 이미 데려가는 축제들의 식별자. */
    private static List<String> festivalIdsOn(Course course) {
        return course.getDays().stream()
                .map(DaySchedule::getSlots)
                .flatMap(List::stream)
                .filter(FestivalSuggestionProvider::isFestivalSlot)
                .map(Slot::getPoiContentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 표준데이터 축제 슬롯인가.
     *
     * <p>TourAPI 축제는 보지 않는다 — 제안은 표준데이터({@code festival_place})에서만 나오므로, 걸러낼
     * 대상도 그쪽 식별자뿐이다. TourAPI 축제를 제안 출처에 더하면(#621) 이 판정도 함께 넓힌다.
     */
    private static boolean isFestivalSlot(Slot slot) {
        return FestivalPlace.parsePublicId(slot.getPoiContentId()).isPresent();
    }
}
