package com.offway.core.transport.service.dto;

import com.offway.core.transport.domain.TransitMode;
import java.util.Objects;

/**
 * 지역에 닿는 수단 하나 — 대표 수단 옆에 함께 내리는 <b>대안</b>(#97).
 *
 * <p><b>왜 대표 하나로 끝내지 않는가.</b> 대표는 우리가 고른 것일 뿐 사용자가 고를 것이 아니다. 완도에
 * 시외버스로도 배로도 갈 수 있는데 하나만 보여 주면, 나머지 하나를 아는 사용자는 화면이 틀렸다고 읽는다.
 *
 * <p>{@link RegionAccess} 전체가 아니라 <b>얇은 값</b>이다. 대안까지 도착 좌표·운행 편을 들면 응답이 두 배가
 * 되는데, 화면이 대안에 대해 묻는 것은 "무엇으로, 어디에, 몇 분" 뿐이다.
 *
 * @param mode 수단
 * @param toName 도착 지점명(역·터미널·항구)
 * @param durationMinutes 소요시간(분, 모르면 null)
 */
public record TransitOption(TransitMode mode, String toName, Integer durationMinutes) {

    public TransitOption {
        Objects.requireNonNull(mode, "수단은 null 일 수 없습니다.");
        Objects.requireNonNull(toName, "도착 지점명은 null 일 수 없습니다.");
    }
}
