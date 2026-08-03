package com.offway.core.itinerary.domain;

import java.time.LocalTime;

/**
 * 하루 안의 시간대 슬롯(course-logic ⑥: 오전/점심/오후/저녁). 관광과 식사를 번갈아 배치할 때의 자리.
 *
 * <p><b>각 슬롯이 자기가 닫히는 시각을 안다.</b> 여행 첫날은 도착 뒤에 남는 슬롯만 쓸 수 있는데(#127), 그 판정을
 * 서비스의 if 문에 두면 시각이 코드 여기저기 흩어진다. 상수가 값을 들고 {@link DayStart} 가 판정을 소유한다.
 *
 * <p>기준이 <b>시작이 아니라 끝</b>인 이유 — 슬롯은 시점이 아니라 구간이다. 오후 3시에 닿아도 오후 관광은 할 수 있다.
 * 시작 시각으로 자르면 "14시 시작인데 15시 도착이니 오후를 놓쳤다" 는 틀린 결론이 난다.
 */
public enum TimeOfDay {

    MORNING("오전", LocalTime.of(12, 0)),
    LUNCH("점심", LocalTime.of(14, 0)),
    AFTERNOON("오후", LocalTime.of(18, 0)),
    DINNER("저녁", LocalTime.of(21, 0));

    private final String label;
    private final LocalTime endsAt;

    TimeOfDay(String label, LocalTime endsAt) {
        this.label = label;
        this.endsAt = endsAt;
    }

    /** 화면 노출 한글 라벨. */
    public String label() {
        return label;
    }

    /** 이 슬롯이 닫히는 시각. 도착이 이보다 늦거나 같으면 그 슬롯은 첫날에 쓸 수 없다. */
    public LocalTime endsAt() {
        return endsAt;
    }

    /** 이 시각에 닿아도 이 슬롯이 아직 열려 있는가. 닫히는 시각 정각이면 남은 시간이 없으므로 못 쓴다. */
    boolean stillOpenAt(LocalTime arrival) {
        return arrival.isBefore(endsAt);
    }
}
