package com.offway.core.itinerary.domain;

import com.offway.core.policy.domain.BenefitScope;

/**
 * 코스 슬롯이 담는 칸의 종류. 장소 세 가지(course-logic ①의 볼거리풀·맛집풀·숙박풀)와, 대중교통 코스에만
 * 붙는 교통 거점 둘이다.
 *
 * <p>관광/맛집이 번갈아 배치되고, 숙박은 멀티데이의 하루 끝에 온다. 교통 거점은 그 바깥 — 첫날 맨 앞의
 * {@link #ARRIVAL} 과 마지막날 맨 뒤의 {@link #DEPARTURE} 다(#415).
 *
 * <h2>두 축을 상수가 들고 있다</h2>
 *
 * <p>새 종류가 생길 때마다 소비하는 쪽(운영시간 조회·시간대 판정·출처 표기)에 {@code if} 가 하나씩 늘던
 * 자리다. 상수가 답을 들고 있으면 <b>상수를 추가하는 순간 두 질문에 답하지 않고는 컴파일이 안 된다</b> —
 * 생성자가 둘 다 받기 때문이다.
 *
 * <ul>
 *   <li>{@link #hasPlace()} — 장소 풀에서 온 칸인가. 아니면 {@code poiContentId} 가 없다
 *   <li>{@link #boundToTimeOfDay()} — 시간대 판정을 타는가. 숙박과 교통 거점은 안 탄다
 * </ul>
 */
public enum SlotKind {

    /** 관광 — 볼거리(자연·역사·문화·레포츠·행사). */
    SIGHT("관광", true, true),

    /** 맛집 — 식사. */
    FOOD("맛집", true, true),

    /** 숙박 — 1박 이상 코스의 잠자리. */
    STAY("숙박", true, false),

    /**
     * 도착 — 대중교통 코스의 첫 칸. 기차에서 내린 역, 버스에서 내린 터미널, 배에서 내린 항구다(#415).
     *
     * <p>지역 안 동선의 기준점이 이미 이 지점이라 <b>값을 새로 구하지 않는다</b> — 코스가 이미 알고
     * 있으면서 화면에는 안 내주던 값을 칸으로 세울 뿐이다.
     */
    ARRIVAL("도착", false, false),

    /** 출발 — 대중교통 코스의 마지막 칸. 돌아가려고 다시 가는 그 지점이다(#415). */
    DEPARTURE("출발", false, false);

    private final String label;
    private final boolean place;
    private final boolean boundToTimeOfDay;

    SlotKind(String label, boolean place, boolean boundToTimeOfDay) {
        this.label = label;
        this.place = place;
        this.boundToTimeOfDay = boundToTimeOfDay;
    }

    /** 화면 노출 한글 라벨. */
    public String label() {
        return label;
    }

    /**
     * 장소 풀(볼거리·맛집·숙박)에서 온 칸인가.
     *
     * <p>아니면 {@code poiContentId} 가 <b>없다</b>. 교통 거점은 우리 DB 의 역·터미널·항구라 장소 상세
     * ({@code GET /api/v1/pois/{id}})로 이어지지 않는다. 가짜 id 를 지어 넣으면 앱이 눌렀을 때 404 를
     * 받으므로, 없는 것은 없는 채로 내린다.
     */
    public boolean hasPlace() {
        return place;
    }

    /**
     * 시간대 판정을 타는가 — 첫날 도착이 늦으면 걷어내지는 칸인가(#127·#214).
     *
     * <p><b>숙박은 안 탄다.</b> 밤늦게 닿아도 잘 곳은 필요하다. <b>교통 거점도 안 탄다</b> — 도착 칸이
     * 곧 그 늦은 도착 자체라, 늦었다는 이유로 도착을 지우면 남는 코스가 어디서 시작하는지가 사라진다.
     */
    public boolean boundToTimeOfDay() {
        return boundToTimeOfDay;
    }

    /**
     * 그 혜택이 붙는 슬롯 — 정책이 말하는 대상을 코스의 언어로 옮긴다(#140).
     *
     * <p><b>대응을 코스 쪽이 소유한다.</b> "숙박세일페스타는 숙소에서 쓴다" 는 프로그램의 성질이라 정책이
     * 알지만, 그게 코스의 어느 자리인지는 코스가 안다. 정책이 {@link SlotKind} 를 들면
     * {@code policy → itinerary} 의존이 생기는데, 코스는 이미 정책을 참조하므로 두 도메인이 서로를
     * 가리키게 된다.
     *
     * <p>{@code switch} 가 {@link BenefitScope} 의 모든 상수를 덮으므로 정책에 새 대상이 생기면 <b>여기서
     * 컴파일이 깨진다</b> — 코스가 자리를 정하지 않은 채로 넘어가지 않는다.
     */
    public static SlotKind covering(BenefitScope scope) {
        return switch (scope) {
            case LODGING -> STAY;
        };
    }
}
