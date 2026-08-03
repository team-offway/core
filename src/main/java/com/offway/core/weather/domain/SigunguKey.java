package com.offway.core.weather.domain;

import java.util.Objects;

/**
 * 시군구를 가리키는 키(#130) — 관광기후지수 응답과 우리 지역을 맞추는 데 쓴다.
 *
 * <p><b>왜 코드가 아니라 이름인가.</b> 관광기후지수가 주는 {@code cityAreaId} 는 법정동코드가 아니다. 실측(2026-08-03)
 * 결과 우리 {@code region.legal_code} 로 조인하면 89곳 중 51곳만 맞는다.
 *
 * <table>
 *   <caption>코드 체계 어긋남</caption>
 *   <tr><th>시도</th><th>기상청 코드</th><th>우리 legal_code</th></tr>
 *   <tr><td>강원</td><td>{@code 42xxx}(특별자치도 전환 전)</td><td>{@code 51xxx}</td></tr>
 *   <tr><td>전북</td><td>{@code 45xxx}(전환 전)</td><td>{@code 52xxx}</td></tr>
 *   <tr><td>광주·전남</td><td>{@code 12xxx} 로 함께 묶임(비표준)</td><td>{@code 29xxx}·{@code 46xxx}</td></tr>
 * </table>
 *
 * <p><b>시군구명으로 맞추면 89곳 전부 맞는다.</b> 다만 이름만으로는 부족하다 — 광역시 구(서구·동구·남구)와 고성군이
 * 여러 시도에 있어 시도를 함께 봐야 갈린다.
 *
 * <p>시도명은 {@link SidoName} 의 축약형으로 접는다. 우리는 {@code 충청북도} 처럼 정식 명칭을 쓰고 기상청은
 * {@code 충북} 을 쓰는데, 앞 두 글자를 자르는 방식으로는 갈라지지 않는다({@code 충청} ≠ {@code 충북}). 그 표가
 * 이미 정확한 대응을 갖고 있다.
 *
 * @param sido 축약 시도명(예: {@code 강원}·{@code 충북}·{@code 경남})
 * @param sigungu 시군구명(예: {@code 정선군}·{@code 동구})
 */
public record SigunguKey(String sido, String sigungu) {

    public SigunguKey {
        Objects.requireNonNull(sido, "시도명은 null 일 수 없습니다.");
        Objects.requireNonNull(sigungu, "시군구명은 null 일 수 없습니다.");
    }

    /**
     * 시도명·시군구명으로 키를 만든다. 어느 쪽이든 비어 있으면 맞출 수 없으므로 null 을 돌려준다.
     *
     * <p>정식 명칭({@code 충청북도})과 축약형({@code 충북})이 같은 키가 된다 — 우리 지역 시드와 기상청 응답이
     * 서로 다른 표기를 쓰기 때문이다.
     */
    public static SigunguKey of(String sido, String sigungu) {
        if (isBlank(sido) || isBlank(sigungu)) {
            return null;
        }
        return new SigunguKey(SidoName.toShort(sido.trim()), sigungu.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
