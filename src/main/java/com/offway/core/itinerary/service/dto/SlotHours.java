package com.offway.core.itinerary.service.dto;

import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.domain.OpeningStatus;

/**
 * 슬롯 하나에 실릴 운영 정보 — 원문과 <b>오늘 판정</b>(#157·#189).
 *
 * <p>둘을 함께 든다. 원문만 주면 클라이언트가 파싱해야 하고, 판정만 주면 "몇 시까지 하는지" 를 못 보여준다.
 *
 * <p>판정은 <b>여행일이 오늘일 때만</b> 채워진다. 다음 주 코스에 지금 시각으로 내린 판정을 붙이면
 * 사용자가 여행일 상태로 읽는다.
 */
public record SlotHours(OpeningHours hours, OpeningStatus status) {

    /** 운영시간을 아직 못 받은 장소. 화면은 그 줄을 지운다. */
    public static SlotHours unknown() {
        return new SlotHours(null, OpeningStatus.UNKNOWN);
    }

    public String useTime() {
        return hours == null ? null : hours.useTime();
    }

    public String restDate() {
        return hours == null ? null : hours.restDate();
    }

    /** 화면에 내릴 상태 — 모르면 null 이라 필드 자체가 사라진다. */
    public String displayStatus() {
        return status != null && status.isDisplayable() ? status.name() : null;
    }
}
