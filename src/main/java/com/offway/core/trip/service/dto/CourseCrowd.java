package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.CrowdChip;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 한 코스가 쓸 혼잡 칩 묶음(#565) — 코스 단위로 한 번 뽑아 둔 것.
 *
 * <p>슬롯마다 조회하면 코스 하나에 질의가 슬롯 수만큼 나간다. 지역과 날짜가 코스 단위로 정해지므로
 * 한 번 읽어 이름으로 꺼내 쓴다.
 *
 * <p><b>장소별이 지역 폴백을 이긴다.</b> 폴백은 그 코스의 장소 전부에 같은 값이 붙는 값이라, 장소를
 * 실제로 아는 값이 있으면 그쪽이 맞다.
 *
 * @param byPlace (장소명, 날짜) → 칩. 집중률 예보에서 온다
 * @param byRegionDate 날짜 → 칩. 지역 요일계수에서 온다 — <b>그 코스의 장소 전부에 같은 값이 붙는다</b>
 */
public record CourseCrowd(Map<Key, CrowdChip> byPlace, Map<LocalDate, CrowdChip> byRegionDate) {

    private static final CourseCrowd EMPTY = new CourseCrowd(Map.of(), Map.of());

    /** 아무 칩도 낼 수 없는 코스 — 날짜를 모르거나 예보·패턴이 둘 다 없다. */
    public static CourseCrowd empty() {
        return EMPTY;
    }

    /**
     * 그 장소·그 날짜의 칩. 둘 다 없으면 빈 값이고 화면은 칩을 안 띄운다.
     *
     * @param placeTitle 장소명 — 우리 {@code region_poi.title}
     */
    public Optional<CrowdChip> of(String placeTitle, LocalDate date) {
        if (placeTitle == null || date == null) {
            return Optional.empty();
        }
        CrowdChip forPlace = byPlace.get(new Key(placeTitle, date));
        if (forPlace != null) {
            return Optional.of(forPlace);
        }
        return Optional.ofNullable(byRegionDate.get(date));
    }

    /** 장소명 + 날짜 — 예보의 자연키에서 지역을 뺀 것이다(이미 지역으로 좁혀 읽었다). */
    public record Key(String attractionName, LocalDate date) {
    }
}
