package com.offway.core.transport.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * TAGO 시내버스가 담는 도시 한 곳({@code getCtyCodeList} 한 항목).
 *
 * <p>이름이 늘 시군구 하나가 아니다. 버스권역이 묶인 곳은 <b>{@code "원주시/횡성군"} 처럼 합본</b>으로 오고, 광역시는 구 단위
 * 없이 <b>시 전체가 한 항목</b>({@code 21} 부산광역시)이다. 두 형태를 {@link #covers(String)} 가 흡수한다.
 */
public record BusCity(int code, String name) {

    /** 버스권역이 묶인 도시의 이름 구분자 — {@code "원주시/횡성군"}. */
    private static final String COMBINED_NAME_DELIMITER = "/";

    /** 시군구 도시코드는 5자리, 시 전체를 한 코드로 두는 광역시는 2자리다. */
    private static final int WIDE_AREA_CODE_LIMIT = 100;

    public BusCity {
        Objects.requireNonNull(name, "도시명은 필수입니다");
    }

    /** 시 전체가 한 코드인가(광역시·특별자치시) — 자치구 이름은 목록에 없지만 전부 커버된다. */
    public boolean isWideArea() {
        return code < WIDE_AREA_CODE_LIMIT;
    }

    /** 이 항목이 해당 시군구를 담는가. */
    public boolean covers(String sigungu) {
        if (isWideArea()) {
            return true;
        }
        return Arrays.stream(name.split(COMBINED_NAME_DELIMITER))
                .map(String::trim)
                .anyMatch(part -> part.equals(sigungu));
    }
}
