package com.offway.core.itinerary.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 사진 있는 후보를 <b>밴드 안에서만</b> 앞으로 당기는 규칙(#545) — 거리 순서를 통째로 뒤집지 않는다.
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>끼니는 거리와 음식 겹침만 봤다(#521 · #533). 그런데 인허가 식당이 75,565건이라 거리로 고르면 그쪽이
 * 이기고, <b>인허가에는 이미지 컬럼 자체가 없다.</b> 89곳 전수 실측(2026-09-09, 대중교통 2박3일)에서
 * 코스 2,395칸 중 565칸이 사진 없이 나갔고 그중 218칸이 끼니였다 — <b>끼니 여섯 칸에 사진이 하나도 없는
 * 지역이 절반을 넘었다</b>(사진 칸 중앙값 0).
 *
 * <p>후보가 없어서가 아니다. 사진 있는 관광 API 식당이 <b>0곳인 지역은 없고</b> 69곳은 6곳 이상이다.
 * 태안은 40곳을 두고 사진 없는 곳을 골랐다.
 *
 * <h2>왜 등급이 아니라 밴드인가</h2>
 *
 * <p>{@link StayPreference}·{@link CafePreference} 는 등급을 <b>완전히</b> 우선한다. 끼니에 같은 방식을
 * 쓰면 사진은 다 채워지지만 동선이 무너진다 — 인허가 식당은 읍내에 몰려 있고 관광 API 식당은 흩어져
 * 있어서다. 실측한 밴드별 비용(89곳, 끼니 6칸, 기준점은 지역 중심):
 *
 * <table border="1">
 *   <caption>밴드별 비용</caption>
 *   <tr><th>밴드</th><th>사진 칸(중앙값)</th><th>합산 이동(중앙값)</th><th>90분위</th><th>6칸 다 사진</th></tr>
 *   <tr><td>지금</td><td>0.0</td><td>1.5㎞</td><td>3.7㎞</td><td>0곳</td></tr>
 *   <tr><td>2㎞</td><td>3.0</td><td>3.5㎞</td><td>6.9㎞</td><td>20곳</td></tr>
 *   <tr><td>3㎞</td><td>3.0</td><td>4.0㎞</td><td>10.0㎞</td><td>28곳</td></tr>
 *   <tr><td><b>5㎞</b></td><td><b>4.0</b></td><td><b>5.6㎞</b></td><td>12.5㎞</td><td>31곳</td></tr>
 *   <tr><td>8㎞</td><td>5.0</td><td>8.3㎞</td><td>22.3㎞</td><td>44곳</td></tr>
 *   <tr><td>12㎞</td><td>6.0</td><td>12.0㎞</td><td>33.3㎞</td><td>53곳</td></tr>
 *   <tr><td>등급 우선(무제한)</td><td>6.0</td><td><b>20.0㎞</b></td><td>79.0㎞</td><td>69곳</td></tr>
 * </table>
 *
 * <p>등급 우선은 <b>동선을 13배</b>로 늘린다. 3㎞ 는 2㎞ 보다 거리만 늘고 사진 칸은 그대로다.
 *
 * <p><b>5㎞ 를 고른 근거는 한계비용이다.</b> 칸 하나를 더 채우는 데 2㎞ 구간은 0.7㎞, 5㎞ 구간은 1.05㎞,
 * 8㎞ 구간부터는 2.7㎞ 가 든다 — 값이 뛰기 직전이 여기다. 그리고 합산 5.6㎞ 는 여섯 끼니를 합친 값이라
 * <b>끼니당 1㎞ 남짓</b>이다. 끼니는 어차피 그날 볼거리 근처로 다시 배정되므로(#533) 이 값이 그대로
 * 이동거리가 되지도 않는다.
 *
 * <h2>순서만 정하고 고르지는 않는다</h2>
 *
 * <p>{@link GeoCluster} 와 같은 계약이다 — <b>인덱스 순서</b>를 주고, 몇 개를 쓸지는 부르는 쪽이 정한다.
 * 끼니의 겹침 회피(#521)가 그 바깥에 있어야 이 안으로 숨지 않는다.
 */
public final class PhotoBand {

    /** 사진 있는 후보를 여기까지만 당겨 온다(㎞). 값의 근거는 이 클래스 문서의 표. */
    public static final double SWAP_LIMIT_KM = 5.0;

    private PhotoBand() {
    }

    /**
     * 거리순 입력을 <b>사진 우선으로 재배치한 인덱스</b>로 돌려준다.
     *
     * <p>앞에서부터 한 자리씩 채운다. 그 자리의 후보에 사진이 있으면 그대로 두고, 없으면 <b>그 후보까지의
     * 거리 + 밴드</b> 안에 있는 가장 가까운 사진 후보와 바꾼다. 바꿀 것이 없으면 원래 후보를 쓴다.
     *
     * <p>한 번 쓴 후보는 다시 안 쓰므로 결과는 입력의 순열이다 — 후보가 사라지거나 늘지 않는다.
     *
     * @param distances 기준점에서 각 후보까지의 거리(㎞). <b>오름차순이어야 한다</b> — 첫 번째로 찾은
     *     사진 후보가 곧 가장 가까운 것이라는 전제가 여기서 나온다
     * @param hasPhoto 같은 순서의 사진 보유 여부
     * @return 재배치된 인덱스. 입력이 비었거나 길이가 다르면 원래 순서
     */
    public static List<Integer> reorder(double[] distances, boolean[] hasPhoto) {
        Objects.requireNonNull(distances, "거리 배열은 null 일 수 없습니다.");
        Objects.requireNonNull(hasPhoto, "사진 여부 배열은 null 일 수 없습니다.");
        if (distances.length != hasPhoto.length) {
            throw new IllegalArgumentException("거리와 사진 여부의 길이가 다릅니다.");
        }

        List<Integer> order = new ArrayList<>(distances.length);
        boolean[] taken = new boolean[distances.length];
        // 자리를 하나씩 채운다. 각 자리의 기준은 **아직 안 쓴 가장 가까운 후보**이고, 그 거리에서 밴드를
        // 잰다 — 이미 당겨 간 것을 기준 삼으면 밴드가 조금씩 밀려 결국 먼 곳까지 당기게 된다.
        int nearestLeft = 0;
        for (int slot = 0; slot < distances.length; slot++) {
            while (nearestLeft < distances.length && taken[nearestLeft]) {
                nearestLeft++;
            }
            if (nearestLeft >= distances.length) {
                break;
            }
            int chosen = hasPhoto[nearestLeft]
                    ? nearestLeft
                    : nearestPhotoWithin(distances, hasPhoto, taken, distances[nearestLeft] + SWAP_LIMIT_KM);
            if (chosen < 0) {
                chosen = nearestLeft;
            }
            taken[chosen] = true;
            order.add(chosen);
        }
        return List.copyOf(order);
    }

    /** 아직 안 쓴 것 중 사진이 있고 {@code limitKm} 안에 있는 첫 후보. 입력이 거리순이라 첫 번째가 가장 가깝다. */
    private static int nearestPhotoWithin(
            double[] distances, boolean[] hasPhoto, boolean[] taken, double limitKm) {
        for (int j = 0; j < distances.length; j++) {
            if (distances[j] > limitKm) {
                // 오름차순이므로 여기서부터는 전부 밴드 밖이다.
                return -1;
            }
            if (!taken[j] && hasPhoto[j]) {
                return j;
            }
        }
        return -1;
    }
}
