package com.offway.core.common.response;

/**
 * 이 응답에 값을 대준 기관 중 <b>출처를 표기해야 하는 곳</b>(#399).
 *
 * <p>표기 규정은 기관명을 요구한다.
 *
 * <blockquote>
 * {@code [O]} 출처: ⓒ한국관광공사 / {@code [X]} TourAPI (API 서비스명만 단독 표기 지양)
 * </blockquote>
 *
 * <p>그래서 {@code ExternalApi} 와 다르다. 그쪽은 <b>어느 API 를 몇 번 불렀나</b>(한도·사용량)를 세는
 * 값이라 국문관광정보·사진갤러리·관광빅데이터가 따로 있지만, 표기에서는 셋 다 한국관광공사 하나다.
 *
 * <h2>교통은 여기 없다 — 빠뜨린 것이 아니다</h2>
 *
 * <p>TMAP·TAGO·코레일도 응답에 값을 대준다(이동시간·도달 지점). 그런데 이 목록의 쓰임이 <b>공모전
 * 출처 표기</b>라, 그 의무가 걸린 공공데이터만 담는다. 교통 쪽은 각자 이용약관이 따로 있고 그 표기
 * 요구는 이 화면 규정과 별개다.
 *
 * <p>여기에 다 넣으면 앱이 <b>표기하지 않아도 될 기관까지 화면에 그린다.</b> 그래서 이름을 "이 응답이
 * 쓴 모든 출처" 가 아니라 "표기 대상" 으로 좁혀 읽는다.
 *
 * <h2>기관명을 서버가 든다</h2>
 *
 * <p>이름만 내보내고 앱이 표로 옮기게 하면, <b>출처가 하나 늘 때 앱이 배포돼야 화면에 뜬다.</b> 매핑
 * 표에 없는 값을 앱은 그릴 수 없다. 표기 누락은 규정 위반이라 그 공백이 그대로 위반이 된다.
 *
 * <p>이 레포가 원래 그렇게 한다 — {@code PolicyType.badgeText()} · {@code SlotKind.label()} ·
 * {@code TransitMode.label()} · {@code PlaceCategory.label()} 이 전부 서버가 든 한글 라벨이다.
 *
 * <p><b>"출처: ⓒ" 접두는 앱이 붙인다.</b> 그건 기관마다 다른 값이 아니라 고정 문구 하나이고, 여러
 * 기관을 한 줄에 잇는 방식도 화면이 정한다. 기관명만 정확히 주면 새 출처가 늘어도 앱은 그대로 그린다.
 */
public enum DataSource {

    KTO("한국관광공사", "국문관광정보 · 관광빅데이터 · 사진갤러리 · 대한민국 구석구석"),

    LOCAL_PERMIT("지방행정인허가데이터개방", "식당 · 숙소 등 인허가 장소"),

    KHS("국가유산청", "국가유산 정보"),

    KMA("기상청", "단기 · 중기 예보와 평년값"),

    KASI("한국천문연구원", "특일정보(공휴일)");

    private final String label;
    private final String detail;

    DataSource(String label, String detail) {
        this.label = label;
        this.detail = detail;
    }

    /** 화면에 그대로 쓰는 기관명. 앱은 여기에 "출처: ⓒ" 만 붙인다. */
    public String label() {
        return label;
    }

    /**
     * 이 기관에서 <b>무엇을</b> 받아 쓰는지.
     *
     * <p>응답에는 안 싣는다 — 화면에 넣으면 출처 한 줄이 문단이 된다. 심사 제출 자료의 "활용 내역"
     * (#402)이 이 값을 읽는다. 코드와 자료가 갈리면 자료가 먼저 낡는다.
     */
    public String detail() {
        return detail;
    }
}
