package com.offway.core.transport.service.dto;

import com.offway.core.transport.domain.OriginCode;
import com.offway.core.transport.domain.OriginHub;
import com.offway.core.transport.domain.OriginHubType;
import com.offway.core.transport.infrastructure.kakao.dto.FoundPlace;
import java.util.Objects;

/**
 * 출발지 제안 한 줄 — 허브와 주소를 같은 모양으로 내린다(#590).
 *
 * <p><b>왜 한 모양인가.</b> 화면은 한 목록이고 사용자는 역·터미널과 주소를 섞어 훑는다. 타입을 갈라
 * 내리면 앱이 두 목록을 합치고 정렬을 다시 해야 하는데, 그 정렬 규칙은 서버가 이미 안다.
 *
 * @param code 앱이 그대로 되돌려 보내는 값. <b>앱은 좌표를 다루지 않는다</b>
 * @param name 화면에 뜨는 이름 — 서울역 · 고속버스터미널(경부·영동) · 분당구청
 * @param area 부제목 — 허브는 시도(서울), 주소는 주소 문자열
 * @param kind 어떤 종류인지. 앱이 아이콘을 가르는 데 쓴다
 */
public record OriginSuggestion(OriginCode code, String name, String area, Kind kind) {

    public OriginSuggestion {
        Objects.requireNonNull(code, "출발지 코드는 필수입니다");
        Objects.requireNonNull(name, "이름은 필수입니다");
        Objects.requireNonNull(area, "부제목은 필수입니다(없으면 빈 문자열)");
        Objects.requireNonNull(kind, "종류는 필수입니다");
    }

    /** 제안의 종류. */
    public enum Kind {
        TRAIN_STATION,
        BUS_TERMINAL,
        /** 우리 허브 목록에 없는 주소·장소. 고르면 서버가 최근접 허브를 찾는다. */
        ADDRESS
    }

    public static OriginSuggestion from(OriginHub hub) {
        Objects.requireNonNull(hub, "허브는 필수입니다");
        return new OriginSuggestion(hub.code(), hub.displayName(), hub.sido().display(), kindOf(hub.type()));
    }

    public static OriginSuggestion from(FoundPlace place) {
        Objects.requireNonNull(place, "장소는 필수입니다");
        return new OriginSuggestion(
                OriginCode.ofCoordinate(place.coordinate()), place.name(), place.address(), Kind.ADDRESS);
    }

    private static Kind kindOf(OriginHubType type) {
        return switch (type) {
            case TRAIN_STATION -> Kind.TRAIN_STATION;
            case BUS_TERMINAL -> Kind.BUS_TERMINAL;
        };
    }
}
