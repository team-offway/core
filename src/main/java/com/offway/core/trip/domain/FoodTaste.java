package com.offway.core.trip.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 상호에서 읽어낸 <b>무슨 음식인가</b> — 같은 것을 두 끼 연달아 넣지 않으려는 것.
 *
 * <h2>왜 카테고리로 안 되나</h2>
 *
 * <p>실측(2026-09-08, 89곳 전수)에서 TourAPI 음식점 분류는 <b>89곳 중 84곳의 대표가 한식</b>이고 그
 * 비중 중앙값이 70% 였다. 꽃게장·칼국수·횟집·한우가 전부 같은 {@code 한식} 한 칸이라, 카테고리를 다르게
 * 고르면 저녁이 카페나 짜장면으로 밀린다 — 태안은 중식 2건·서양식 1건이 전부다.
 *
 * <p>인허가 분류(13종)도 {@code KOREAN} 이 52% 라 같은 한계가 있다. 갈비·한우·국밥이 한 칸에 뭉친다.
 *
 * <h2>그래서 상호를 읽는다</h2>
 *
 * <p>실제로 사용자가 느끼는 겹침이 거기 있다 — 횡성 {@code 횡성한우마을} · {@code 횡성순한우}, 태안
 * {@code 꽃지원조꽃게집} · {@code 꽃게장집}. 지역 특산이 곧 음식점 풀이라 어디서나 같은 모양이다.
 *
 * <h2>분류는 각 출처가 풀어서 준다</h2>
 *
 * <p>인허가는 {@code PlaceCategory}, TourAPI 는 {@code cat3} 로 코드 체계가 다르다. <b>그 해석은 각
 * 출처가 소유한다</b> — 여기서 외부 코드를 읽으면 도메인이 API 세부에 묶인다.
 *
 * <h2>못 읽는 것이 3분의 2다</h2>
 *
 * <p>인허가 9만 건에서 상호로 음식이 읽히는 것은 <b>33%</b> 다({@code 가든}·{@code 식당}·{@code 소담}
 * 처럼 음식이 안 들어간 이름이 많다). 그래서 <b>모르면 다른 음식으로 본다</b> — 확신 없이 후보를 빼면
 * 사진 있는 좋은 카드가 근거 없이 밀려난다. 겹침을 덜 막는 쪽이 잘못 막는 쪽보다 낫다.
 *
 * <p>사전은 실측 빈도 상위부터 넣었다. 긴 말이 먼저 와야 한다 — {@code 막국수} 가 {@code 국수} 보다,
 * {@code 닭갈비} 가 {@code 갈비} 보다 앞이다. 뒤집히면 막국수가 국수로, 닭갈비가 갈비로 뭉친다.
 */
public enum FoodTaste {

    CRAB, BEEF, PORK, CHICKEN, NOODLE, COLD_NOODLE, BUCKWHEAT, CHINESE, JAPANESE, CUTLET,
    WESTERN, RAW_FISH, SHELLFISH, EEL, SOUP, RICE_SOUP, SUNDAE, TOFU, BIBIMBAP, BUFFET,
    CAFE, BAKERY, SNACK;

    /**
     * 상호에 나타나는 말 → 음식. <b>삽입 순서가 곧 우선순위다</b>({@link LinkedHashMap}).
     *
     * <p>긴 말을 먼저 둔다 — {@code 닭갈비} 가 {@code 갈비} 뒤에 오면 닭갈비집이 갈비로 읽힌다.
     */
    private static final Map<String, FoodTaste> BY_WORD = new LinkedHashMap<>();

    static {
        BY_WORD.put("간장게장", CRAB);
        BY_WORD.put("꽃게", CRAB);
        BY_WORD.put("대게", CRAB);
        BY_WORD.put("게장", CRAB);
        BY_WORD.put("닭갈비", CHICKEN);
        BY_WORD.put("삼계", CHICKEN);
        BY_WORD.put("백숙", CHICKEN);
        BY_WORD.put("치킨", CHICKEN);
        BY_WORD.put("통닭", CHICKEN);
        BY_WORD.put("한우", BEEF);
        BY_WORD.put("갈비", BEEF);
        BY_WORD.put("소고기", BEEF);
        BY_WORD.put("흑돼지", PORK);
        BY_WORD.put("삼겹", PORK);
        BY_WORD.put("족발", PORK);
        BY_WORD.put("보쌈", PORK);
        BY_WORD.put("돼지", PORK);
        BY_WORD.put("막국수", BUCKWHEAT);
        BY_WORD.put("메밀", BUCKWHEAT);
        BY_WORD.put("칼국수", NOODLE);
        BY_WORD.put("국수", NOODLE);
        BY_WORD.put("우동", NOODLE);
        BY_WORD.put("냉면", COLD_NOODLE);
        BY_WORD.put("짜장", CHINESE);
        BY_WORD.put("짬뽕", CHINESE);
        BY_WORD.put("반점", CHINESE);
        BY_WORD.put("중화", CHINESE);
        BY_WORD.put("돈까스", CUTLET);
        BY_WORD.put("돈가스", CUTLET);
        BY_WORD.put("초밥", JAPANESE);
        BY_WORD.put("스시", JAPANESE);
        BY_WORD.put("라멘", JAPANESE);
        BY_WORD.put("스테이크", WESTERN);
        BY_WORD.put("파스타", WESTERN);
        BY_WORD.put("피자", WESTERN);
        BY_WORD.put("버거", WESTERN);
        BY_WORD.put("물회", RAW_FISH);
        BY_WORD.put("횟집", RAW_FISH);
        BY_WORD.put("활어", RAW_FISH);
        BY_WORD.put("생선회", RAW_FISH);
        BY_WORD.put("바지락", SHELLFISH);
        BY_WORD.put("조개", SHELLFISH);
        BY_WORD.put("전복", SHELLFISH);
        BY_WORD.put("굴", SHELLFISH);
        BY_WORD.put("장어", EEL);
        BY_WORD.put("매운탕", SOUP);
        BY_WORD.put("추어탕", SOUP);
        BY_WORD.put("아구", SOUP);
        BY_WORD.put("곰탕", RICE_SOUP);
        BY_WORD.put("설렁탕", RICE_SOUP);
        BY_WORD.put("국밥", RICE_SOUP);
        BY_WORD.put("해장", RICE_SOUP);
        BY_WORD.put("순대", SUNDAE);
        BY_WORD.put("청국장", TOFU);
        BY_WORD.put("두부", TOFU);
        BY_WORD.put("비빔밥", BIBIMBAP);
        BY_WORD.put("보리밥", BIBIMBAP);
        BY_WORD.put("뷔페", BUFFET);
        BY_WORD.put("베이커리", BAKERY);
        BY_WORD.put("제과", BAKERY);
        BY_WORD.put("디저트", BAKERY);
        BY_WORD.put("카페", CAFE);
        BY_WORD.put("커피", CAFE);
        BY_WORD.put("떡볶이", SNACK);
        BY_WORD.put("김밥", SNACK);
        BY_WORD.put("분식", SNACK);
    }

    /** 상호에서 음식을 읽는다. 못 읽으면 빈 값 — 그때는 <b>다른 음식으로 본다</b>. */
    public static Optional<FoodTaste> of(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String normalized = title.toLowerCase(Locale.KOREAN);
        for (Map.Entry<String, FoodTaste> entry : BY_WORD.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * 상호를 먼저 보고, 못 읽으면 <b>이미 풀어 둔 분류</b>를 쓴다.
     *
     * <p>순서가 중요하다 — 분류를 먼저 보면 {@code 횡성한우마을}(인허가 {@code KOREAN})이 분류에서
     * 걸리지 않아 상호까지 못 간다. 상호가 <b>더 좁은 근거</b>이므로 먼저다.
     *
     * <p><b>여기서 외부 코드를 읽지 않는다.</b> {@code A05020200} 같은 값은 TourAPI 의 구현 세부라,
     * 도메인이 그걸 알면 외부 API 에 묶인다. 코드를 이 값으로 옮기는 것은 각 출처의 어댑터가 한다.
     */
    public static Optional<FoodTaste> of(String title, FoodTaste category) {
        Optional<FoodTaste> byTitle = of(title);
        return byTitle.isPresent() ? byTitle : Optional.ofNullable(category);
    }

    /**
     * 두 후보가 <b>같은 음식이라고 말할 수 있는가</b>.
     *
     * <p>한쪽이라도 못 읽으면 {@code false} 다 — 모르는 것을 같다고 하면 멀쩡한 후보를 지운다.
     * 실측에서 상호로 읽히는 것이 33% 뿐이라, 이 보수적인 선택이 결과를 좌우한다.
     */
    public static boolean same(String oneTitle, FoodTaste oneCategory, String otherTitle, FoodTaste otherCategory) {
        Optional<FoodTaste> a = of(oneTitle, oneCategory);
        Optional<FoodTaste> b = of(otherTitle, otherCategory);
        return a.isPresent() && a.equals(b);
    }
}
