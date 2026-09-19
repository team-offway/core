package com.offway.core.transport.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 출발지 허브가 속한 시·도 — 저장 표기와 화면 표기를 함께 소유한다(#590).
 *
 * <p><b>왜 별도 enum 인가.</b> {@code BusSido} 는 TAGO 도시코드를 다루는 것이고, 이쪽은 <b>사용자가
 * 치는 말과 화면에 뜨는 말</b>을 다룬다. 같은 "시도" 라는 낱말을 쓰지만 답하는 질문이 다르다.
 *
 * <p><b>저장 표기는 통합 이전 것이다.</b> {@code region.sido} 는 {@code 전남광주통합특별시} 를 쓰는데,
 * 허브를 그 표기로 저장하면 <b>"광주" 검색에 전남 132곳이 전부 걸린다</b> — 목포·여수 터미널이 광주
 * 검색에 뜨는 것은 틀렸다. 그래서 DB 에는 판정한 값 그대로(광주광역시·전라남도) 넣고, 여기서 화면
 * 표기로 옮긴다.
 *
 * <p><b>화면 표기는 짧은 이름이다.</b> 제안 목록의 부제목 자리라서 {@code 전남광주통합특별시} 처럼
 * 긴 정식 명칭을 넣으면 한 줄이 이름보다 길어진다. 목적지(지역 카드)는 {@code region.sido} 의 정식
 * 명칭을 그대로 쓰므로 축이 다르고, 둘이 어긋나는 것이 아니다.
 *
 * <p><b>검색 토큰을 따로 두지 않는 이유</b> — 저장 표기와 화면 표기 둘만으로 사용자가 치는 말이 전부
 * 걸린다. {@code 충북} 은 화면 표기에, {@code 충청} 은 저장 표기({@code 충청북도})에 담긴다.
 */
public enum OriginSido {
    SEOUL("서울특별시", "서울"),
    BUSAN("부산광역시", "부산"),
    DAEGU("대구광역시", "대구"),
    INCHEON("인천광역시", "인천"),
    GWANGJU("광주광역시", "광주"),
    DAEJEON("대전광역시", "대전"),
    ULSAN("울산광역시", "울산"),
    SEJONG("세종특별자치시", "세종"),
    GYEONGGI("경기도", "경기"),
    GANGWON("강원도", "강원"),
    CHUNGBUK("충청북도", "충북"),
    CHUNGNAM("충청남도", "충남"),
    JEONBUK("전라북도", "전북"),
    JEONNAM("전라남도", "전남"),
    GYEONGBUK("경상북도", "경북"),
    GYEONGNAM("경상남도", "경남"),
    JEJU("제주특별자치도", "제주");

    /** DB 에 저장된 판정 결과 표기(2018 경계 기준). */
    private final String stored;

    /** 제안 목록에 뜨는 짧은 이름. */
    private final String display;

    OriginSido(String stored, String display) {
        this.stored = stored;
        this.display = display;
    }

    public String stored() {
        return stored;
    }

    public String display() {
        return display;
    }

    /**
     * 저장 표기로 찾는다.
     *
     * <p><b>없을 수 있다</b> — 좌표가 없어 판정하지 못한 허브는 {@code sido} 가 NULL 이다. 그런 허브는
     * 애초에 제안에 오르지 않으므로(동선에 못 올린다) 부재가 정상 결과다.
     */
    public static Optional<OriginSido> ofStored(String stored) {
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(sido -> sido.stored.equals(stored))
                .findFirst();
    }

    /** 이 시도가 검색어에 걸리는가 — 저장 표기와 화면 표기 양쪽을 본다. */
    public boolean matches(String normalizedQuery) {
        return stored.contains(normalizedQuery) || display.contains(normalizedQuery);
    }
}
