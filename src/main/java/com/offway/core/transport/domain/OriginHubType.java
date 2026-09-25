package com.offway.core.transport.domain;

import java.util.List;

/**
 * 출발지로 고를 수 있는 허브의 종류 — 표시 이름 규칙을 상수별로 소유한다(#590).
 *
 * <p><b>표시 이름을 규칙으로 푸는 이유.</b> 원본 이름은 업계 표기라 사용자가 부르는 말과 다르다 —
 * TAGO 는 서울역을 {@code 서울}, 동서울터미널을 {@code 동서울} 로 준다. 900곳에 표시 이름을 손으로
 * 달면 그 목록이 곧 낡고, 새 허브가 들어올 때 누가 채우는지가 불분명해진다. 그래서 <b>접미를 붙이는
 * 규칙</b>을 기본으로 두고, 규칙으로 안 되는 소수만 {@link OriginHubAlias} 가 덮는다.
 *
 * <p><b>이미 접미가 있으면 붙이지 않는다.</b> {@code 수원터미널}·{@code 천안아산역} 처럼 원본에 이미
 * 종류가 들어간 이름이 있어, 무조건 붙이면 {@code 천안아산역터미널} 이 된다.
 */
public enum OriginHubType {
    /** 기차역 — {@code 서울} → {@code 서울역}. */
    TRAIN_STATION("TRAIN", "역"),

    /** 버스 터미널 — {@code 동서울} → {@code 동서울터미널}. 고속·시외를 가리지 않는다. */
    BUS_TERMINAL("BUS", "터미널");

    /**
     * 이미 종류가 들어간 이름으로 판정할 접미 목록.
     *
     * <p>자기 접미만 보면 안 된다 — 버스 터미널인 {@code 천안아산역} 은 {@code 역} 으로 끝나는데 그건
     * 버스 접미가 아니라서, 자기 접미만 검사하면 {@code 천안아산역터미널} 이 된다.
     */
    private static final List<String> HUB_SUFFIXES = List.of("역", "터미널", "정류소", "항", "공항");

    /** {@link OriginCode} 가 쓰는 접두 — 코드 하나로 종류까지 알 수 있게 한다. */
    private final String codePrefix;

    private final String suffix;

    OriginHubType(String codePrefix, String suffix) {
        this.codePrefix = codePrefix;
        this.suffix = suffix;
    }

    public String codePrefix() {
        return codePrefix;
    }

    /** 원본 이름에 종류 접미를 붙인 표시 이름. 이미 종류가 들어간 이름은 그대로 둔다. */
    public String displayNameOf(String rawName) {
        if (HUB_SUFFIXES.stream().anyMatch(rawName::endsWith)) {
            return rawName;
        }
        return rawName + suffix;
    }
}
