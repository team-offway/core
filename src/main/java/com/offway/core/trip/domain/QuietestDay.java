package com.offway.core.trip.domain;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

/**
 * 그 지역이 <b>가장 한산한 요일</b>과 그 격차(#394).
 *
 * <p>연차를 하루 옮길 이유가 되는 값이다 — "화요일에 가장 한산해요".
 *
 * @param dayOfWeek 가장 한산한 요일
 * @param percentLessThanOtherDays 나머지 요일들보다 몇 % 적은가. 항상 양수다 —
 *     {@link WeeklyVisitPattern#quietest()} 가 격차가 의미 있을 때만 이 값을 만든다
 */
public record QuietestDay(DayOfWeek dayOfWeek, int percentLessThanOtherDays) {

    public QuietestDay {
        Objects.requireNonNull(dayOfWeek, "요일은 null 일 수 없습니다.");
        if (percentLessThanOtherDays <= 0) {
            // 0 이하면 "가장 한산한 요일" 이 나머지보다 많다는 뜻이라 계산이 어긋난 것이다.
            throw new IllegalArgumentException("한산 격차는 양수여야 합니다: " + percentLessThanOtherDays);
        }
    }

    /**
     * 화면에 그대로 쓰는 한글 요일 — "화요일".
     *
     * <p><b>서버가 한글 라벨을 든다.</b> 이 레포가 원래 그렇게 한다({@code PolicyType.badgeText()} ·
     * {@code TransitMode.label()} · {@code DataSource.label()}). 클라이언트마다 요일 표기를 따로
     * 만들면 iOS·안드로이드·웹이 조금씩 달라진다.
     *
     * <p>로케일을 명시한다 — 서버 기본 로케일에 맡기면 운영 환경 설정에 따라 "Tuesday" 가 나간다.
     */
    public String label() {
        return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN);
    }
}
