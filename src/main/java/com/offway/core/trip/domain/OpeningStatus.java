package com.offway.core.trip.domain;

/**
 * 오늘 이 장소가 열려 있는가(#189) — <b>여행일이 오늘일 때만</b> 판정한다.
 *
 * <p><b>왜 서버가 판정하나.</b> 관광 API 가 운영시간·휴무일을 자유 텍스트로 준다. 클라이언트마다 파싱하면
 * 클라이언트마다 다르게 틀린다. 판정 결과를 enum 으로 내려 한 곳에서 책임진다.
 *
 * <p><b>모르면 모른다고 한다.</b> 실측(공주·정선 관광지 30건)에서 확실히 판정 가능한 것은 70% 였다.
 * 계절별({@code [하절기] 09:00~18:00 / [동절기] 10:00~17:00})·복수시설 형식을 억지로 파싱하지 않는다 —
 * 나머지 30% 를 위해 70% 의 신뢰를 깎을 이유가 없다.
 *
 * <p><b>잘못된 단정이 침묵보다 나쁘다.</b> "오늘 휴무" 가 틀리면 갈 수 있는 곳을 안 가고, "영업중" 이
 * 틀리면 헛걸음한다. 애매하면 말하지 않는 쪽이 낫다.
 */
public enum OpeningStatus {

    /** 지금 열려 있다. */
    OPEN("영업 중"),

    /** 오늘이 휴무일이다. */
    CLOSED_TODAY("오늘은 휴무일이에요"),

    /** 오늘 운영은 끝났다 — 열긴 하는데 지금은 시간이 지났다. */
    CLOSED_NOW("오늘 운영이 끝났어요"),

    /** 판정할 수 없다. 화면은 아무것도 표시하지 않는다. */
    UNKNOWN(null);

    private final String message;

    OpeningStatus(String message) {
        this.message = message;
    }

    /** 화면에 그대로 나갈 문구. {@link #UNKNOWN} 은 없다 — 표시할 것이 없다는 뜻이다. */
    public String message() {
        return message;
    }

    /** 클라이언트에 내릴 값인가. 모르는 것은 필드 자체를 안 내려 화면이 그 줄을 그리지 않게 한다. */
    public boolean isDisplayable() {
        return this != UNKNOWN;
    }
}
