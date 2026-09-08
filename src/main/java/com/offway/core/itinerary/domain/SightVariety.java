package com.offway.core.itinerary.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * 하루에 <b>같은 종류를 몇 곳까지</b> 넣을지 세는 자(#522).
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>실제 코스에서 같은 것이 몰린다 — 태안 2일차가 해수욕장 3곳, 양양 1일차와 보령 1일차가 유적 3곳이다.
 * 후보 구성이 그렇다: 태안 관광지 78건 중 <b>해수욕장 30건(38%) + 항구 21건(27%)</b> 이라, 거리로만
 * 고르면 상위가 그 분류로 채워진다.
 *
 * <h2>두 층으로 센다</h2>
 *
 * <p>한 층만으로는 안 된다는 것이 실측에서 나왔다.
 *
 * <ul>
 *   <li><b>좁은 칸만 보면</b> 태안 1일차를 못 잡는다 — 해수욕장·항구·해안이 서로 다른 코드라 중복이 0으로
 *       세어지는데, 사람 눈에는 <b>바다 4곳</b>이다.
 *   <li><b>넓은 칸만 보면</b> 산·계곡·해변이 한 칸으로 뭉쳐 과하게 걸린다. 그건 다양성이 아니라 제약이다.
 * </ul>
 *
 * <p>그래서 좁은 칸은 {@value #SAME_KIND_PER_DAY} 곳, 넓은 칸은 {@value #SAME_FAMILY_PER_DAY} 곳으로
 * 둔다. 양양 1일차의 유적 3곳은 좁은 칸에서, 태안 1일차의 바다 4곳은 넓은 칸에서 걸린다.
 *
 * <h2>못 채우면 물러난다</h2>
 *
 * <p>후보가 얇은 지역이 있다. 실측에서 정선(48건)·신안(57건)은 상한을 걸면 동선이 +20㎞ 늘었다 — 대체할
 * 것이 멀리 있기 때문이다. 반대로 후보가 두꺼운 곳은 <b>중앙값 +0㎞</b> 였고 가평은 -6.5㎞, 양양은
 * -5.3㎞ 로 오히려 줄었다(한 분류에 몰린 곳들이 서로 멀었다).
 *
 * <p>그래서 고정 상한이 아니라 <b>후보가 허락하는 만큼</b>이다. 호출자가 상한을 풀어 가며 다시 고른다 —
 * <b>빈 슬롯이 중복보다 나쁘다.</b>
 */
public final class SightVariety {

    /** 좁은 칸(해수욕장·항구·유적)은 하루 한 곳. "해수욕장 두 곳" 이 안 나오게 하는 선이다. */
    public static final int SAME_KIND_PER_DAY = 1;

    /** 넓은 칸(자연·역사·휴양)은 하루 세 곳. 바다만 넷은 막되 자연 셋은 허용한다. */
    public static final int SAME_FAMILY_PER_DAY = 3;

    /**
     * 넓은 칸의 길이 — TourAPI 분류 체계에서 {@code A0101}(자연관광지) 처럼 다섯 자다.
     *
     * <p>여기서 <b>코드를 해석하지 않는다.</b> 같은 종류인지 견주기만 하므로 값의 뜻을 알 필요가 없다.
     */
    private static final int FAMILY_LENGTH = 5;

    private final Map<String, Integer> kinds = new HashMap<>();
    private final Map<String, Integer> families = new HashMap<>();
    private final int kindLimit;
    private final int familyLimit;

    private SightVariety(int kindLimit, int familyLimit) {
        this.kindLimit = kindLimit;
        this.familyLimit = familyLimit;
    }

    /** 기본 — 좁은 칸 한 곳, 넓은 칸 세 곳. */
    public static SightVariety strict() {
        return new SightVariety(SAME_KIND_PER_DAY, SAME_FAMILY_PER_DAY);
    }

    /**
     * 상한을 {@code step} 만큼 푼 자.
     *
     * <p>후보가 얇아 슬롯을 못 채울 때 호출자가 한 단계씩 물러난다. {@code step} 이 충분히 크면 상한이
     * 사실상 없어져 지금까지의 동작(거리순)으로 돌아간다.
     */
    public static SightVariety relaxedBy(int step) {
        return new SightVariety(SAME_KIND_PER_DAY + step, SAME_FAMILY_PER_DAY + step);
    }

    /**
     * 이 종류를 하루에 더 넣어도 되나.
     *
     * <p>종류를 모르면 <b>넣어도 되는 것으로 본다</b>. 우리 DB 출처(인허가·국가유산)는 이 값이 없는데,
     * 모르는 것을 같다고 묶으면 그것들끼리 한 칸이 되어 하루에 하나밖에 못 들어간다.
     */
    public boolean accepts(String kind) {
        if (kind == null || kind.isBlank()) {
            return true;
        }
        return kinds.getOrDefault(kind, 0) < kindLimit
                && families.getOrDefault(familyOf(kind), 0) < familyLimit;
    }

    /** 넣기로 한 것을 센다. */
    public void add(String kind) {
        if (kind == null || kind.isBlank()) {
            return;
        }
        kinds.merge(kind, 1, Integer::sum);
        families.merge(familyOf(kind), 1, Integer::sum);
    }

    private static String familyOf(String kind) {
        return kind.length() <= FAMILY_LENGTH ? kind : kind.substring(0, FAMILY_LENGTH);
    }
}
