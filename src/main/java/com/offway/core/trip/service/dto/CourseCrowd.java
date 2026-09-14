package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.CrowdChip;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 한 코스가 쓸 혼잡 칩 묶음(#565) — 코스 단위로 한 번 뽑아 둔 것.
 *
 * <p>슬롯마다 조회하면 코스 하나에 질의가 슬롯 수만큼 나간다. 지역과 날짜가 코스 단위로 정해지므로
 * 한 번 읽어 이름으로 꺼내 쓴다.
 *
 * <h2>폴백은 "모르는 장소" 에만 쓴다</h2>
 *
 * <p>지역 요일계수는 그 코스의 장소 전부에 같은 값이 붙는다. 장소를 <b>실제로 아는</b> 값이 있으면
 * 그쪽이 맞고, 그 값이 "보통" 이어도 마찬가지다 — 집중률 50 인 곳에 "토요일엔 붐비는 지역" 을 붙이면
 * 우리가 재어 놓고도 더 거친 말로 덮는 셈이다.
 *
 * <p>그래서 <b>칩이 없는 것</b>과 <b>예보 자체가 없는 것</b>을 갈라 든다. {@link #byPlace} 에는 문턱을
 * 넘은 것만 들어가므로, 그것만으로는 둘을 구분할 수 없다.
 *
 * @param byPlace (장소명, 날짜) → 칩. 문턱을 넘은 것만 있다
 * @param measured 예보를 <b>가진</b> (장소명, 날짜). 값이 문턱 사이여서 칩이 없는 것도 여기 있다
 * @param byRegionDate 날짜 → 칩. 지역 요일계수에서 온다 — <b>그 코스의 장소 전부에 같은 값이 붙는다</b>
 */
public record CourseCrowd(
        Map<Key, CrowdChip> byPlace, Set<Key> measured, Map<LocalDate, CrowdChip> byRegionDate) {

    private static final CourseCrowd EMPTY = new CourseCrowd(Map.of(), Set.of(), Map.of());

    /** 아무 칩도 낼 수 없는 코스 — 날짜를 모르거나 예보·패턴이 둘 다 없다. */
    public static CourseCrowd empty() {
        return EMPTY;
    }

    /**
     * 그 장소·그 날짜의 칩. 낼 것이 없으면 빈 값이고 화면은 칩을 안 띄운다.
     *
     * <p>순서가 곧 규칙이다 — 장소별 칩 → (예보는 있는데 문턱 사이면) 없음 → 지역 폴백.
     *
     * @param placeTitle 장소명 — 우리 {@code region_poi.title}
     */
    public Optional<CrowdChip> of(String placeTitle, LocalDate date) {
        if (placeTitle == null || date == null) {
            return Optional.empty();
        }
        Key key = new Key(placeTitle, date);
        CrowdChip forPlace = byPlace.get(key);
        if (forPlace != null) {
            return Optional.of(forPlace);
        }
        if (measured.contains(key)) {
            // 재어 봤고 보통이었다. 폴백으로 내려가면 아는 것을 모르는 값으로 덮는다.
            return Optional.empty();
        }
        return Optional.ofNullable(byRegionDate.get(date));
    }

    /** 장소명 + 날짜 — 예보의 자연키에서 지역을 뺀 것이다(이미 지역으로 좁혀 읽었다). */
    public record Key(String attractionName, LocalDate date) {
    }
}
