package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 사진 있는 후보를 밴드 안에서만 당기는 규칙(#545).
 *
 * <p>여기서 틀리면 두 방향으로 조용히 나빠진다 — 안 당기면 코스 칸이 빈 채로 나가고, 너무 당기면 동선이
 * 무너진다. 실측에서 밴드를 없애면 끼니 합산 이동이 1.5㎞ 에서 20.0㎞ 로 늘었다.
 */
class PhotoBandTest {

    @Test
    void 사진이_없는_자리를_밴드_안의_사진_후보로_바꾼다() {
        // 0번은 사진이 없고, 3㎞ 떨어진 1번에 사진이 있다 — 밴드(5㎞) 안이라 바꾼다.
        double[] distances = {1.0, 4.0};
        boolean[] photos = {false, true};

        assertEquals(List.of(1, 0), PhotoBand.reorder(distances, photos));
    }

    @Test
    void 밴드_밖의_사진_후보는_당기지_않는다() {
        // negative control — 이게 없으면 "무조건 사진 우선" 구현도 위 테스트를 통과한다.
        // 그러면 인허가 식당이 읍내에 몰린 지역에서 끼니가 수십 ㎞ 밖으로 흩어진다.
        double[] distances = {1.0, 100.0};
        boolean[] photos = {false, true};

        assertEquals(List.of(0, 1), PhotoBand.reorder(distances, photos));
    }

    @Test
    void 경계값은_밴드에_든다() {
        // 1.0 + 5.0 = 6.0 — 딱 맞는 거리는 바꾸는 쪽이다.
        assertEquals(List.of(1, 0), PhotoBand.reorder(new double[] {1.0, 6.0}, new boolean[] {false, true}));
    }

    @Test
    void 경계값을_넘으면_안_바꾼다() {
        assertEquals(List.of(0, 1), PhotoBand.reorder(new double[] {1.0, 6.01}, new boolean[] {false, true}));
    }

    @Test
    void 이미_사진이_있으면_그대로_둔다() {
        // 사진 있는 자리를 건드리면 가까운 것을 두고 먼 것을 앞세우게 된다.
        double[] distances = {1.0, 2.0, 3.0};
        boolean[] photos = {true, true, true};

        assertEquals(List.of(0, 1, 2), PhotoBand.reorder(distances, photos));
    }

    @Test
    void 사진이_하나도_없으면_거리_순서를_지킨다() {
        double[] distances = {1.0, 2.0, 3.0};
        boolean[] photos = {false, false, false};

        assertEquals(List.of(0, 1, 2), PhotoBand.reorder(distances, photos));
    }

    @Test
    void 한_번_당긴_후보를_다시_쓰지_않는다() {
        // 사진이 하나뿐인데 앞자리 둘이 다 비었다 — 첫 자리만 가져가고 둘째 자리는 원래 것을 쓴다.
        double[] distances = {1.0, 2.0, 3.0};
        boolean[] photos = {false, false, true};

        // 0번 자리에 2번(사진)을 당기고, 그다음 0번과 1번이 순서대로 남는다.
        assertEquals(List.of(2, 0, 1), PhotoBand.reorder(distances, photos));
    }

    @Test
    void 결과는_입력의_순열이라_후보가_사라지지_않는다() {
        // 여기가 깨지면 코스 칸이 조용히 빈다 — 재정렬이 후보를 잃으면 안 된다.
        double[] distances = {0.5, 1.0, 2.0, 3.0, 9.0};
        boolean[] photos = {false, true, false, true, true};

        List<Integer> order = PhotoBand.reorder(distances, photos);

        assertEquals(distances.length, order.size());
        assertEquals(List.of(0, 1, 2, 3, 4), order.stream().sorted().toList());
    }

    @Test
    void 후보가_없으면_빈_순서다() {
        assertEquals(List.of(), PhotoBand.reorder(new double[] {}, new boolean[] {}));
    }

    @Test
    void 길이가_다르면_거절한다() {
        // 두 배열이 같은 후보를 가리킨다는 것이 이 계약의 전제다. 어긋나면 조용히 엉뚱한 후보를 당긴다.
        assertThrows(IllegalArgumentException.class,
                () -> PhotoBand.reorder(new double[] {1.0}, new boolean[] {true, false}));
    }
}
