package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.trip.domain.PetFriendlyPlace;
import com.offway.core.trip.repository.PetFriendlyPlaceRepository;
import com.offway.core.trip.service.dto.PetAccompany;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 코스에 실린 장소에 반려동반 가능 표식을 붙인다(#566).
 *
 * <p>{@code FestivalPeriodProvider}·{@code OpeningHoursProvider} 와 같은 자리다 — 슬롯에 붙는 상세를
 * <b>코스당 한 번의 조회</b>로 가져온다. 슬롯마다 읽으면 N+1 이 되고, 그 곱셈이 그대로 사용자 대기가 된다.
 *
 * <h2>슬롯을 고르는 데 쓰지 않는다</h2>
 *
 * <p>이 값은 <b>표시 전용</b>이다. 반려동반 장소만으로 코스를 채우는 것은 불가능하다 — 89곳 음식이
 * 10건이라 끼니가 막힌다(#555 실측). "반려동물 우선 선별" 은 코스 생성 로직·요청 필드·앱 화면이 함께
 * 필요한 별 작업이고, 팀 결정(2026-09-13)은 칩만 먼저다.
 *
 * <h2>지역으로 한 번 읽는다</h2>
 *
 * <p>코스는 한 지역 안에서 만들어지고 그 지역의 반려동반 장소는 평균 일곱 곳이다(442건 / 67지역).
 * 콘텐츠 ID 목록으로 묻는 대신 <b>지역 것을 통째로 받아</b> 슬롯과 맞춘다 — 인덱스가 그 모양이고,
 * 슬롯 수와 무관하게 질의가 하나다.
 *
 * <h2>모르는 것을 "불가" 로 내리지 않는다</h2>
 *
 * <p>인허가·국가유산·고캠핑 출처의 슬롯은 {@code poiContentId} 체계가 달라 판정할 수 없다. 그때는
 * <b>키가 없다</b> — 화면이 칩을 안 띄우는 것과 "반려동물 안 됨" 을 말하는 것은 다르다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetAccompanyProvider {

    private final PetFriendlyPlaceRepository petFriendlyPlaceRepository;

    /**
     * 코스 안 장소들의 반려동반 정보를 한 번에 가져온다.
     *
     * <p><b>슬롯에 콘텐츠 ID 가 없으면 질의 자체가 없다.</b> 교통 거점 칸(도착·출발)만 있는 코스가
     * 그렇고, 그 경우 이 기능이 비용을 0 으로 유지한다.
     *
     * @return 콘텐츠 ID → 반려동반 정보. 반려동반이 아닌 장소는 <b>키가 없다</b>
     */
    public Map<String, PetAccompany> forCourse(Course course) {
        Set<String> contentIds = course.getDays().stream()
                .map(DaySchedule::getSlots)
                .flatMap(List::stream)
                .map(Slot::getPoiContentId)
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (contentIds.isEmpty()) {
            return Map.of();
        }

        List<PetFriendlyPlace> places = petFriendlyPlaceRepository.findByRegionId(course.getRegionId());
        if (places.isEmpty()) {
            return Map.of();
        }

        Map<String, PetAccompany> byContentId = new HashMap<>();
        for (PetFriendlyPlace place : places) {
            if (contentIds.contains(place.getContentId())) {
                byContentId.put(place.getContentId(), PetAccompany.from(place));
            }
        }
        return Map.copyOf(byContentId);
    }
}
