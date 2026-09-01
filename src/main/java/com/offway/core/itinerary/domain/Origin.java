package com.offway.core.itinerary.domain;

import com.offway.core.transport.domain.Coordinate;
import java.util.Objects;

/**
 * 코스가 출발하는 곳(#382) — <b>좌표와 이름을 한 몸으로</b> 든다.
 *
 * <p>둘을 따로 두지 않는 이유는 <b>이름만 있는 출발지가 의미가 없기</b> 때문이다. 좌표 없이 "서울" 만
 * 남으면 도달시간도 거리도 못 재고, 코스는 그 이름으로 아무것도 할 수 없다. 반대로 좌표만 있는 것은
 * 지금까지의 상태고 그건 정상이다 — 화면이 "서울에서 출발" 한 줄을 못 그릴 뿐이다.
 *
 * <p><b>서버는 좌표를 이름으로 바꾸지 못한다.</b> 역지오코딩이 필요한데 그건 앱이 이미 할 수 있고(기기
 * 내장), 서버가 하려면 외부 호출이 하나 더 늘어난다. 그래서 이름은 앱이 저장할 때 실어 보내고 서버는
 * 받아 두었다가 상세에서 되돌려주기만 한다.
 *
 * @param point 출발 좌표 — 이것 없이는 출발지가 아니다
 * @param name 화면에 쓸 짧은 지명(`서울`·`성남`). <b>없을 수 있다</b> — 지오코딩이 실패했거나 이 필드를
 *     모르는 앱이다
 */
public record Origin(Coordinate point, String name) {

    /**
     * 이름의 길이 상한.
     *
     * <p>앱이 시·도/시·군 단위의 짧은 이름을 보내기로 했다(`특별시`·`광역시`·`도` 접미는 앱이 뗀다).
     * 가장 긴 지명도 이 안에 들어온다.
     */
    public static final int MAX_NAME_LENGTH = 20;

    public Origin {
        Objects.requireNonNull(point, "출발 좌표는 null 일 수 없습니다.");
    }

    /**
     * 좌표와 이름으로 출발지를 만든다 — <b>이름이 이상하면 이름만 버린다</b>.
     *
     * <p>길이가 넘거나 공백뿐이면 거절하지 않고 null 로 둔다. 이 값은 화면 한 줄을 채우는 곁가지인데,
     * 그것 때문에 코스 담기가 통째로 실패하면 주객이 뒤집힌다. 없으면 화면은 그 조각만 접는다.
     */
    public static Origin of(Coordinate point, String name) {
        return new Origin(point, usableName(name));
    }

    private static String usableName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH ? null : trimmed;
    }

    public double lat() {
        return point.lat();
    }

    public double lng() {
        return point.lng();
    }
}
