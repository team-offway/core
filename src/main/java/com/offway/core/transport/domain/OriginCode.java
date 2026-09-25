package com.offway.core.transport.domain;

import com.offway.core.common.geo.Coordinate;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * 앱이 출발지로 되돌려 보내는 값 — <b>앱은 좌표를 다루지 않는다</b>(#590).
 *
 * <p><b>왜 코드 한 개로 받나.</b> 위치 수집을 그만두는 것이 이 작업의 목적이고, 앱이 좌표를 들고
 * 있으면 그 값이 어디서 왔는지(사용자가 고른 지점인지 기기 위치인지) 서버가 구별할 수 없다. 서버가
 * 발급한 코드만 되돌려 받으면 <b>출발지는 우리가 내려준 목록 안의 값</b>임이 형태로 보장된다.
 *
 * <p>문법은 셋이다.
 *
 * <pre>
 *   TRAIN:NAT010000      기차역 — 코드는 TAGO nodeid
 *   BUS:NAEK030          버스 터미널 — 코드는 TAGO 터미널 코드
 *   GEO:37.5665,126.9780 주소·장소 — 허브가 아닌 지점
 * </pre>
 *
 * <p><b>{@code GEO} 가 있는 이유.</b> 허브가 아닌 주소를 고를 수 있어야 하는데(사용자가 "분당" 을
 * 치는 경우) 그 지점에는 우리 코드가 없다. 좌표를 코드 안에 담으면 앱은 여전히 <b>불투명한 문자열
 * 하나</b>만 들고 다니고, 필드가 갈라지지 않는다.
 *
 * <p>그 값이 기기 위치일 수도 있다는 점은 남는다. 다만 그건 앱이 우리 제안 목록을 거치지 않고
 * 위조하는 경우이고, 서버가 막을 수 있는 종류의 일이 아니다 — 우리가 지는 의무는 <b>우리가 수집하지
 * 않는 것</b>이고 그 형태는 지켜진다.
 */
public record OriginCode(String value) {

    private static final String DELIMITER = ":";

    /** 허브가 아닌 지점의 접두. */
    private static final String GEO_PREFIX = "GEO";

    private static final String GEO_DELIMITER = ",";

    /** 코드 길이 상한 — 제안 목록에서 온 값이라 짧다. 긴 입력은 우리가 준 값이 아니다. */
    public static final int MAX_LENGTH = 64;

    public OriginCode {
        Objects.requireNonNull(value, "출발지 코드는 필수입니다");
    }

    public static OriginCode ofHub(OriginHubType type, String hubCode) {
        Objects.requireNonNull(type, "허브 종류는 필수입니다");
        Objects.requireNonNull(hubCode, "허브 코드는 필수입니다");
        return new OriginCode(type.codePrefix() + DELIMITER + hubCode);
    }

    public static OriginCode ofCoordinate(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "좌표는 필수입니다");
        return new OriginCode(
                GEO_PREFIX + DELIMITER + coordinate.lat() + GEO_DELIMITER + coordinate.lng());
    }

    /** 이 코드가 가리키는 허브 종류. 좌표 코드면 비어 있다. */
    public Optional<OriginHubType> hubType() {
        return Arrays.stream(OriginHubType.values())
                .filter(type -> value.startsWith(type.codePrefix() + DELIMITER))
                .findFirst();
    }

    /** 접두를 뗀 허브 코드. 허브 코드가 아니면 부른 쪽이 잘못이다. */
    public String hubCode() {
        OriginHubType type = hubType()
                .orElseThrow(() -> new IllegalStateException("허브 코드가 아닙니다: " + value));
        return value.substring(type.codePrefix().length() + DELIMITER.length());
    }

    public boolean isCoordinate() {
        return value.startsWith(GEO_PREFIX + DELIMITER);
    }

    /**
     * 좌표 코드에 담긴 좌표.
     *
     * <p><b>형식이 깨져 있으면 계약 위반이다</b> — 우리가 발급한 코드를 그대로 되돌려 보내는 자리라
     * 값이 망가졌다는 것은 앱이 손댔거나 잘못 저장했다는 뜻이다. 여기서는 그 사실만 알리고, 상태·
     * 메시지 결정은 부르는 쪽(도메인 예외)에 맡긴다.
     */
    public Optional<Coordinate> coordinate() {
        if (!isCoordinate()) {
            return Optional.empty();
        }
        // **빈 조각을 보존한다**(limit = -1). 기본 split 은 끝의 빈 문자열을 버려
        // `GEO:37.5,127.0,` 가 두 조각으로 읽히고, 망가진 코드가 정상으로 통과한다.
        String[] parts = value.substring(GEO_PREFIX.length() + DELIMITER.length())
                .split(GEO_DELIMITER, -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    new Coordinate(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())));
        } catch (IllegalArgumentException e) {
            // 숫자 파싱 실패(NumberFormatException)와 Coordinate 의 불변식 위반(범위 밖·NaN)을 한 번에
            // 받는다 — 전자가 후자의 하위 타입이고, 둘 다 "쓸 수 없는 코드" 라는 같은 결과다.
            return Optional.empty();
        }
    }
}
