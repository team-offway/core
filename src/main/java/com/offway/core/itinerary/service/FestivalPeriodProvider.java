package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.trip.domain.FestivalPeriod;
import com.offway.core.trip.domain.FestivalPlace;
import com.offway.core.trip.domain.PoiContentType;
import com.offway.core.trip.repository.FestivalPeriodRepository;
import com.offway.core.trip.repository.FestivalPlaceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 코스에 실린 축제가 언제 열리는지(#388).
 *
 * <p>{@code OpeningHoursProvider} 와 같은 자리다 — 슬롯에 붙는 외부 상세를 <b>코스당 한 번의 조회</b>로
 * 가져온다. 슬롯마다 읽으면 N+1 이 되고, 그 곱셈이 그대로 사용자 대기가 된다.
 *
 * <h2>왜 화면에 보여주나</h2>
 *
 * <p>후보를 고를 때 이미 "그날 안 하는 축제" 는 뺐다({@code RegionPoiService}). 남은 축제는 여행일에
 * 열리는 것들인데, <b>며칠까지 하는지는 사용자가 알아야 한다</b> — 1박 2일로 갔는데 축제가 첫날로 끝나면
 * 둘째 날 일정이 헛돈다.
 *
 * <h2>출처가 둘이다</h2>
 *
 * <p>처음에는 TourAPI 축제만 봤다({@code contentTypeId == 15}). 그런데 <b>코스에 실제로 들어가는
 * 축제는 표준데이터다</b> — 89곳에서 TourAPI 축제는 후보 컷에 거의 안 든다(#392). 표준데이터 축제는
 * {@code contentId} 가 {@code FST-} 이고 {@code contentTypeId} 가 0 이라 그 필터에 안 걸렸고, 걸려도
 * {@code festival_period} 는 TourAPI 키로 잡혀 있어 못 찾았다.
 *
 * <p>그래서 <b>기간 칸이 늘 비어 있었다.</b> 운영 실측(2026-09-29)에서 코스에 실린 축제 한 건이
 * 표준데이터였고, 기간이 안 나갔다(#622). 기간은 이미 갖고 있었다 — {@code festival_place} 행에
 * {@code event_start}·{@code event_end} 가 있다.
 *
 * <p>이제 두 출처를 함께 본다. <b>판정을 타입 하나에 걸지 않는 것</b>이 요점이다 — 식별자 모양이
 * 출처를 말해 주므로 그것으로 가른다.
 *
 * <p>기간을 모르는 축제는 키가 없다. 화면은 그 줄을 그리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FestivalPeriodProvider {

    /** TourAPI 축제 콘텐츠 타입 — 이 타입만 물어본다. 다른 슬롯까지 넣으면 조회가 쓸데없이 커진다. */
    private static final int FESTIVAL_TYPE = PoiContentType.FESTIVAL.contentTypeId();

    private final FestivalPeriodRepository festivalPeriodRepository;
    private final FestivalPlaceRepository festivalPlaceRepository;

    /**
     * 코스 안 축제들의 기간을 한 번에 가져온다 — 출처별로 <b>각각 한 번</b>.
     *
     * <p><b>축제가 없으면 질의 자체가 없다.</b> 대부분의 코스가 그렇고, 그 경우 이 기능이 비용을 0 으로
     * 유지한다. 한 출처만 있으면 그쪽만 묻는다.
     *
     * <p>키는 슬롯의 {@code poiContentId} 그대로다 — 화면이 슬롯에서 곧바로 찾아 쓴다.
     */
    public Map<String, FestivalPeriod> forCourse(Course course) {
        List<String> contentIds = course.getDays().stream()
                .map(DaySchedule::getSlots)
                .flatMap(List::stream)
                .filter(FestivalPeriodProvider::isFestival)
                .map(Slot::getPoiContentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (contentIds.isEmpty()) {
            return Map.of();
        }

        Map<String, FestivalPeriod> periods = new LinkedHashMap<>();
        periods.putAll(tourApiPeriods(contentIds));
        periods.putAll(standardPeriods(contentIds));
        return Map.copyOf(periods);
    }

    /** TourAPI 축제 — {@code content_id} 로 기간 테이블을 읽는다. */
    private Map<String, FestivalPeriod> tourApiPeriods(List<String> contentIds) {
        List<String> ids = contentIds.stream()
                .filter(id -> FestivalPlace.parsePublicId(id).isEmpty())
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return festivalPeriodRepository.findByContentIds(ids);
    }

    /**
     * 표준데이터 축제 — 기간이 {@code festival_place} 행에 이미 있다.
     *
     * <p>별도 기간 테이블이 없어 행을 읽어 {@link FestivalPeriod} 로 옮긴다. 화면이 두 출처를 구별할
     * 이유가 없으므로 같은 타입으로 내린다.
     */
    private Map<String, FestivalPeriod> standardPeriods(List<String> contentIds) {
        Map<Long, String> idByKey = new LinkedHashMap<>();
        contentIds.forEach(contentId ->
                FestivalPlace.parsePublicId(contentId).ifPresent(id -> idByKey.put(id, contentId)));
        if (idByKey.isEmpty()) {
            return Map.of();
        }
        Map<String, FestivalPeriod> found = new LinkedHashMap<>();
        festivalPlaceRepository.findByIds(idByKey.keySet()).forEach((id, festival) ->
                found.put(idByKey.get(id), toPeriod(festival)));
        return found;
    }

    private static FestivalPeriod toPeriod(FestivalPlace festival) {
        return FestivalPeriod.builder()
                .contentId(festival.publicId())
                .eventStart(festival.getEventStart())
                .eventEnd(festival.getEventEnd())
                .title(festival.getName())
                .fetchedAt(festival.getFetchedAt())
                .build();
    }

    /**
     * 축제인가 — <b>두 출처를 함께 본다</b>(#622).
     *
     * <p>{@code poiContentTypeId} 는 <b>nullable</b> 이고, 표준데이터 축제는 그 값이 0 이다. 그래서
     * 타입만으로는 못 가른다 — 식별자가 {@code FST-} 로 시작하는지도 함께 본다.
     *
     * <p>인허가·국가유산도 타입이 0 인데, 그쪽은 식별자 접두어가 달라 여기 걸리지 않는다.
     */
    private static boolean isFestival(Slot slot) {
        Integer contentTypeId = slot.getPoiContentTypeId();
        if (contentTypeId != null && contentTypeId == FESTIVAL_TYPE) {
            return true;
        }
        return FestivalPlace.parsePublicId(slot.getPoiContentId()).isPresent();
    }
}
