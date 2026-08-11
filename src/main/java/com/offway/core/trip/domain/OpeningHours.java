package com.offway.core.trip.domain;

/**
 * 장소의 운영시간·휴무일(#157) — 코스 슬롯에 인라인으로 나가는 두 값.
 *
 * <p>{@link PoiIntro} 는 카테고리별 보조정보 전부를 담지만, <b>슬롯이 필요한 건 이 둘뿐</b>이다. 슬롯에
 * 대표메뉴·객실수까지 실으면 코스 응답이 무거워지고, 그건 상세를 눌렀을 때 볼 것이다.
 *
 * <p>둘 다 <b>자유 텍스트</b>다. 관광 API 가 {@code 상시 개방}·{@code 09:00~18:00}·
 * {@code [하절기] 09:00~18:00 / [동절기] 10:00~17:00} 처럼 제각각 준다. 여기서는 그대로 들고, 해석은
 * 필요한 곳에서 한다(#189).
 */
public record OpeningHours(String useTime, String restDate) {

    /** 둘 다 없으면 실을 이유가 없다 — 빈 값을 내리면 화면이 빈 줄을 그린다. */
    public boolean isEmpty() {
        return (useTime == null || useTime.isBlank()) && (restDate == null || restDate.isBlank());
    }
}
