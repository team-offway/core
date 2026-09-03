package com.offway.core.transport.service.dto;

import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.TransitMode;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * 지역에 닿는 수단 하나 — 대표 수단 옆에 함께 내리는 <b>대안</b>(#97).
 *
 * <p><b>왜 대표 하나로 끝내지 않는가.</b> 대표는 우리가 고른 것일 뿐 사용자가 고를 것이 아니다. 완도에
 * 시외버스로도 배로도 갈 수 있는데 하나만 보여 주면, 나머지 하나를 아는 사용자는 화면이 틀렸다고 읽는다.
 *
 * <p>{@link RegionAccess} 전체가 아니라 <b>얇은 값</b>이다. 도착 좌표는 들지 않는다 — 그건 코스 동선의
 * 기준점이라 대표 수단에만 뜻이 있다.
 *
 * <p><b>시간표는 대안도 든다</b>(#414). "무엇으로, 어디에, 몇 분" 만으로는 사용자가 대안을 고를 수 없다 —
 * 시외버스가 40분 더 걸려도 지금 바로 타는 편이 있으면 그쪽을 고른다. 그 판단에 시각이 필요하다.
 *
 * @param mode 수단
 * @param toName 도착 지점명(역·터미널·항구)
 * @param durationMinutes 소요시간(분, 모르면 null)
 * @param departures 그날 탈 수 있는 편들. 조회창 밖이거나 그날 운행이 없으면 <b>빈 목록</b>이다
 */
@Builder
public record TransitOption(
        TransitMode mode, String toName, Integer durationMinutes, List<Departure> departures) {

    public TransitOption {
        Objects.requireNonNull(mode, "수단은 null 일 수 없습니다.");
        Objects.requireNonNull(toName, "도착 지점명은 null 일 수 없습니다.");
        departures = departures == null ? List.of() : List.copyOf(departures);
    }
}
