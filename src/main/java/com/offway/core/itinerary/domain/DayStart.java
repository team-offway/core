package com.offway.core.itinerary.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 하루를 <b>언제부터 쓸 수 있는가</b> — 그날 남아 있는 시간대 슬롯(#127).
 *
 * <p>여행 첫날은 이동에 먹힌다. 서울에서 4시간 걸려 오후 2시에 닿았는데 1일차 오전 일정을 넣으면, 코스가 지킬 수 없는 약속이
 * 된다. OffWay 는 <b>LNT(가용시간)가 핵심 개념</b>이라 이 과대계산이 특히 아프다 — 연차를 그만큼 잘못 쓰게 만든다.
 *
 * <p>둘째 날부터는 하루 전부를 쓴다. 자차이거나 도착 시각을 모를 때({@code NO_SERVICE_ON_DATE}·조회 실패)도 마찬가지다 —
 * 모르는 것을 늦은 도착으로 단정해 일정을 깎으면, 조회 실패가 조용히 코스 품질을 떨어뜨린다.
 *
 * @param usableSlots 아직 쓸 수 있는 시간대. 밤늦게 닿으면 빈 집합일 수 있다(그날은 숙박만)
 */
public record DayStart(Set<TimeOfDay> usableSlots) {

    public DayStart {
        usableSlots = Set.copyOf(usableSlots);
    }

    /** 하루 전부를 쓴다 — 둘째 날부터, 그리고 도착 시각을 모르는 모든 경우. */
    public static DayStart fullDay() {
        return new DayStart(Set.of(TimeOfDay.values()));
    }

    /** 그날은 일정이 없다 — 자정을 넘겨 닿는 경우. 숙박만 남는다. */
    public static DayStart none() {
        return new DayStart(Set.of());
    }

    /**
     * 그 날짜에 <b>이 시각에 닿는다면</b> 첫날에 남는 시간대.
     *
     * <p>자정을 넘겨 닿으면 그날은 통째로 이동이다 — 시각만 보면 새벽 도착이 "오전부터 여유" 로 둔갑한다.
     *
     * <p>생성과 날짜 수정이 <b>같은 규칙을 써야 한다</b>(#214). 한쪽에만 두면 날짜를 옮겼을 때 도착 시각과
     * 일정이 서로 다른 날짜를 근거로 삼는 코스가 만들어진다.
     *
     * @param travelDate 여행 시작일. 모르면 null — 자정 넘김을 판정할 기준이 없어 도착 시각만 본다
     */
    public static DayStart afterArriving(LocalDate travelDate, LocalDateTime arriveAt) {
        if (travelDate != null && arriveAt.toLocalDate().isAfter(travelDate)) {
            return none();
        }
        return arrivingAt(arriveAt.toLocalTime());
    }

    /** 이 시각에 닿았을 때 남는 슬롯 — 아직 닫히지 않은 것들. */
    public static DayStart arrivingAt(LocalTime arrival) {
        return new DayStart(Arrays.stream(TimeOfDay.values())
                .filter(slot -> slot.stillOpenAt(arrival))
                .collect(Collectors.toUnmodifiableSet()));
    }

    public boolean allows(TimeOfDay slot) {
        return usableSlots.contains(slot);
    }

    /**
     * 볼거리를 몇 곳까지 넣을 수 있는가. 관광 슬롯은 오전·오후 둘뿐이라, 죽은 쪽 몫만큼 줄어든다.
     *
     * <p>여기서 줄인 만큼은 <b>사라지지 않고 다음 날로 밀린다</b> — 호출자가 이 수만큼만 잘라 쓰므로 남은 후보가 그대로
     * 이튿날 몫이 된다.
     */
    public int sightCapacity(int perDaySights) {
        return morningShare(perDaySights) + afternoonShare(perDaySights);
    }

    /** 식사를 몇 끼 넣을 수 있는가 — 점심·저녁 중 아직 안 지난 것. 늦게 닿으면 그만큼 줄어든다. */
    public int mealCapacity() {
        return (allows(TimeOfDay.LUNCH) ? 1 : 0) + (allows(TimeOfDay.DINNER) ? 1 : 0);
    }

    /**
     * 이 개수를 넣을 때 오전에 갈 몫. 오전을 못 쓰면 0 이라 전부 오후로 간다.
     *
     * <p>나누는 규칙은 하루 배치와 같아야 한다 — 홀수면 오전이 하나 더 가진다.
     */
    public int morningShare(int sights) {
        return allows(TimeOfDay.MORNING) ? (sights + 1) / 2 : 0;
    }

    private int afternoonShare(int sights) {
        return allows(TimeOfDay.AFTERNOON) ? sights / 2 : 0;
    }
}
