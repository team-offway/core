package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.trip.domain.FestivalPeriod;
import com.offway.core.trip.domain.PoiContentType;
import com.offway.core.trip.repository.FestivalPeriodRepository;
import java.util.List;
import java.util.Map;
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
 * <p>기간을 모르는 축제는 키가 없다. 화면은 그 줄을 그리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FestivalPeriodProvider {

    /** 축제 콘텐츠 타입 — 이 타입만 물어본다. 다른 슬롯까지 넣으면 조회가 쓸데없이 커진다. */
    private static final int FESTIVAL_TYPE = PoiContentType.FESTIVAL.contentTypeId();

    private final FestivalPeriodRepository festivalPeriodRepository;

    /**
     * 코스 안 축제들의 기간을 한 번에 가져온다.
     *
     * <p><b>축제가 없으면 질의 자체가 없다.</b> 대부분의 코스가 그렇고, 그 경우 이 기능이 비용을 0 으로
     * 유지한다.
     */
    public Map<String, FestivalPeriod> forCourse(Course course) {
        List<String> festivalIds = course.getDays().stream()
                .map(DaySchedule::getSlots)
                .flatMap(List::stream)
                .filter(FestivalPeriodProvider::isFestival)
                .map(Slot::getPoiContentId)
                .distinct()
                .toList();
        if (festivalIds.isEmpty()) {
            return Map.of();
        }
        return festivalPeriodRepository.findByContentIds(festivalIds);
    }

    /**
     * 축제인가.
     *
     * <p>{@code poiContentTypeId} 는 <b>nullable</b> 이다 — 인허가·국가유산처럼 TourAPI 콘텐츠가 아닌
     * 슬롯이 있다. 그때는 축제가 아니다.
     */
    private static boolean isFestival(Slot slot) {
        Integer contentTypeId = slot.getPoiContentTypeId();
        return contentTypeId != null && contentTypeId == FESTIVAL_TYPE;
    }
}
