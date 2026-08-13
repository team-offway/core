package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.repository.PoiIntroRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 코스에 실린 장소들의 운영시간을 <b>DB 에서만</b> 읽는다(#157).
 *
 * <p>요청 경로에서 외부를 부르지 않는다. 슬롯마다 {@code detailIntro2} 를 부르면 코스 하나에 20건이 넘어
 * 관광정보 한도가 코스 40개면 마른다. 받아 두는 일은 배치({@code PoiIntroRefreshService})가 한다.
 *
 * <p><b>아직 안 받은 장소는 그냥 빈다.</b> 화면은 있으면 보여주고 없으면 그 줄을 지운다 — 없는 것을
 * 지어내는 것보다 늦게 채워지는 편이 낫다.
 */
@Component
@RequiredArgsConstructor
public class OpeningHoursProvider {

    private final PoiIntroRepository poiIntroRepository;

    /** 코스 전체 슬롯의 운영시간을 한 번의 조회로 가져온다 — 슬롯마다 읽으면 N+1 이 된다. */
    public Map<String, OpeningHours> forCourse(Course course) {
        List<String> contentIds = course.getDays().stream()
                .map(DaySchedule::getSlots)
                .flatMap(List::stream)
                .map(Slot::getPoiContentId)
                .distinct()
                .toList();
        return poiIntroRepository.findByContentIds(contentIds);
    }
}
