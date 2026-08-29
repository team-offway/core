package com.offway.core.itinerary.domain;

import com.offway.core.transport.domain.Coordinate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 후보 풀에서 <b>코스에 실제로 쓸 수 있는 것만</b> 남긴다(#335).
 *
 * <p>{@link GeoCluster} 와 같은 자리다 — 좌표만 보는 순수 계산이라 단위 테스트로 망라하고, 서비스는 조율만
 * 한다. 반환은 <b>입력 인덱스</b>라 호출부가 자기 후보 타입을 그대로 재배열한다.
 *
 * <h2>거르는 것 둘</h2>
 *
 * <p><b>① 경로를 못 만드는 좌표.</b> TMAP 이 도로에 못 붙이는 좌표(귀목봉 · 해발 1,036m 산 정상)가 코스에
 * 들어가면 그 코스는 방문 순서와 이동시간이 조용히 직선거리로 떨어진다. 200 으로 정상 응답하므로 사용자는
 * 산을 직선으로 넘는 시간을 보면서도 틀린 값인지 알 수 없다.
 *
 * <p><b>② 같은 좌표의 중복.</b> 인허가 풀은 31.4%(38,136건)가 다른 장소와 좌표를 공유한다. 지오코딩 오류가
 * 아니라 <b>실제 집합체</b>다 — 한 좌표에 천북굴단지 점포 71개, 용평리조트 시설 49개, 예산시장 47개가
 * 실제로 있다. 그런데 그중 넷이 한 날에 뽑히면 화면에 <b>"이동 0분" 슬롯이 네 개 연속</b>으로 뜬다
 * (운영 코스 67, 평창 3일차가 그랬다).
 */
public final class CandidatePool {

    private CandidatePool() {
    }

    /**
     * 쓸 수 있는 후보의 인덱스 — 차단 좌표를 빼고, 같은 좌표는 하나만 남긴다. 입력 순서를 지킨다.
     *
     * <p>같은 좌표에서 <b>어느 것을 남길지는 씨앗이 정한다.</b> 늘 첫 번째를 남기면 재생성(#114)이 같은
     * 자리에서 다른 가게를 못 뽑아 "다른 코스" 가 그만큼 좁아진다.
     *
     * <p>차단 여부를 <b>집합이 아니라 술어로</b> 받는다. 그 판정은 TMAP 이 무엇을 거절했는지 아는
     * transport 의 몫이고, 저장 자릿수를 맞추는 키 규격도 그쪽에 있다. 여기는 "빼라고 하면 뺀다" 까지만
     * 알면 되므로, 판정 방식이 바뀌어도 이 클래스는 그대로다.
     *
     * <p>같은 좌표를 묶는 데는 {@link Coordinate} 를 그대로 쓴다. 한 풀 안에서 비교하는 것이라 값이 같으면
     * 같은 자리이고, 저장·조회를 오갈 때 생기는 자릿수 문제와는 무관하다.
     *
     * @param points 후보 좌표(호출부 리스트와 같은 순서)
     * @param blocked 경로를 못 만드는 좌표인가. 항상 거짓이면 ① 은 아무것도 안 한다
     * @param seed 같은 좌표 무리에서 하나를 고르는 씨앗
     */
    public static List<Integer> usable(List<Coordinate> points, Predicate<Coordinate> blocked, long seed) {
        Map<Coordinate, List<Integer>> sharing = new LinkedHashMap<>();
        for (int i = 0; i < points.size(); i++) {
            Coordinate point = points.get(i);
            if (blocked.test(point)) {
                continue;
            }
            sharing.computeIfAbsent(point, ignored -> new ArrayList<>()).add(i);
        }
        return sharing.values().stream().map(indexes -> pick(indexes, seed)).toList();
    }

    /** 씨앗을 무리 크기로 접어 하나를 고른다. 음수 씨앗도 유효한 인덱스가 되게 floorMod 를 쓴다. */
    private static int pick(List<Integer> indexes, long seed) {
        return indexes.get((int) Math.floorMod(seed, indexes.size()));
    }
}
