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
 * @param status 이 수단의 상태(#508). <b>모르는 것과 없는 것을 가른다</b> — 소요시간이 null 인 이유가
 *     "아직 안 쟀다"({@code POINT_ONLY})인지 "노선이 없다"({@code NO_ROUTE})인지에 따라 화면이 할 말이
 *     다르다. 전자는 비워 두면 되고 후자는 그렇게 적어야 한다
 * @param durationMinutes 소요시간(분, 모르면 null)
 * @param departures 그날 탈 수 있는 편들. 조회창 밖이거나 그날 운행이 없으면 <b>빈 목록</b>이다
 */
@Builder
public record TransitOption(
        TransitMode mode, String toName, RegionAccess.Status status,
        Integer durationMinutes, List<Departure> departures) {

    public TransitOption {
        Objects.requireNonNull(mode, "수단은 null 일 수 없습니다.");
        Objects.requireNonNull(status, "수단 상태는 null 일 수 없습니다.");
        Objects.requireNonNull(toName, "도착 지점명은 null 일 수 없습니다.");
        // 대안 목록도 같은 규칙을 탄다 — 여기는 마스터 이름을 직접 받으므로 RegionAccess 의
        // 정규화를 안 거친다. 한쪽만 붙이면 대표는 "태안터미널", 대안은 "태안" 이 된다.
        //
        // **검사 뒤에 둔다.** 앞에 두면 mode 가 null 일 때 "수단은 null 일 수 없습니다" 대신
        // NullPointerException 이 나가 무엇이 잘못됐는지가 안 보인다.
        toName = mode.placeName(toName);
        departures = departures == null ? List.of() : List.copyOf(departures);
    }
}
