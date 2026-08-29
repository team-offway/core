package com.offway.core.curation.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 큐레이션 링크를 내릴 화면(#341).
 *
 * <p><b>면을 나누는 이유는 끌 수 있게 하려는 것이다.</b> 요구는 "모든 곳에 다 넣는다" 였지만, 홈 배너와 장소
 * 상세에 똑같은 목록이 다 뜨면 화면이 지저분해진다. 전부 켜서 시작하되 나중에 면별로 뺄 수 있어야 후회가 없다.
 *
 * <p>DB 에는 {@code "HOME,REGION"} 처럼 쉼표로 잇는 한 칸으로 넣는다. 별도 테이블로 쪼개면 조인이 하나 느는데,
 * 값이 넷뿐이고 늘어날 일이 드물어 그 비용이 값어치를 넘는다.
 */
public enum Surface {

    /** 홈. */
    HOME,

    /** 지역 상세. */
    REGION,

    /** 코스 상세. */
    COURSE,

    /** 장소 상세. */
    POI;

    private static final String SEPARATOR = ",";

    /**
     * 저장된 문자열을 면 집합으로 되돌린다.
     *
     * <p><b>모르는 이름은 건너뛴다.</b> {@code valueOf} 는 예외를 던지는데, 이 값은 DB 에 문자열로 남아 있어
     * 상수명을 바꾸거나 지우면 기존 행과 어긋난다. 그때 예외가 나면 그 행 하나 때문에 <b>화면이 통째로
     * 500</b> 이 된다 — 링크 하나가 안 보이는 편이 낫다.
     *
     * <p>순서를 지키려고 {@link LinkedHashSet} 을 쓴다. {@code Set.of} 는 순서가 없어 저장·조회에서 값이
     * 뒤섞이고, 그러면 같은 내용인데 문자열이 달라져 diff 가 의미 없이 뜬다.
     */
    public static Set<Surface> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(stored.split(SEPARATOR))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(Surface::byName)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 면 집합을 저장 형태로. enum 선언 순서로 적어, 같은 집합이면 언제나 같은 문자열이 된다. */
    public static String join(Set<Surface> surfaces) {
        return Arrays.stream(values())
                .filter(surfaces::contains)
                .map(Surface::name)
                .collect(Collectors.joining(SEPARATOR));
    }

    private static Optional<Surface> byName(String name) {
        return Arrays.stream(values()).filter(surface -> surface.name().equals(name)).findFirst();
    }
}
