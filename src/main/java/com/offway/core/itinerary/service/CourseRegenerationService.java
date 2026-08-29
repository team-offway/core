package com.offway.core.itinerary.service;

import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.itinerary.service.dto.RegeneratedCourse;
import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.service.UnroutableCoordinateService;
import com.offway.core.trip.service.RegionPoiService;
import com.offway.core.trip.service.dto.RegionPois;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * "이 코스 말고 다른 걸로" — 코스 재생성(#114).
 *
 * <p><b>왜 따로 필요한가.</b> 생성은 결정론적이다. 같은 입력이면 같은 코스가 나오므로, 재생성 버튼을 눌러도 화면이
 * 그대로다. 그래서 이 작업의 본질은 엔드포인트 추가가 아니라 <b>생성에 다양성 축을 넣는 것</b>이고, 그 축은
 * {@code GeoCluster.selectCompact} 의 씨앗이다 — 씨앗이 다르면 다른 군집이 잡히지만 뭉치는 성질은 남아 동선이
 * 망가지지 않는다.
 *
 * <p><b>후보가 적으면 다르게 만들 수 없다.</b> 인구감소지역은 볼거리 후보가 필요 개수와 비슷한 경우가 흔하다. 그때
 * 조용히 같은 코스를 주면 사용자는 버튼이 고장 난 줄 안다 — 결과에 "더 다른 코스를 못 만들었다" 를 담아 알린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseRegenerationService {

    /**
     * 새 씨앗을 몇 번까지 시도할지.
     *
     * <p><b>시도는 외부를 부르지 않는다.</b> 씨앗 판정은 {@code selectedSightIds} 로 좌표 계산만 하고, 실제 코스
     * 조립은 이긴 씨앗 하나로 <b>딱 한 번</b> 한다. 판정하자고 코스를 통째로 짜면 TMAP 경유지 최적화가 시도 횟수
     * ×일수만큼 곱해지는데, 그 API 는 <b>일일 허용량이 50건</b>이라 요청 한 번이 그날 몫을 태운다.
     *
     * <p>그래도 상한을 두는 이유는 좌표 계산도 공짜가 아니고, 후보가 적은 지역에서는 몇 번을 돌려도 같은 결과라
     * 더 도는 게 의미가 없기 때문이다.
     */
    private static final int MAX_ATTEMPTS = 8;

    /**
     * "충분히 다르다" 의 기준 — 직전 코스와 장소가 이 비율 미만으로 겹치면 다른 코스로 본다.
     *
     * <p>절반까지 같으면 사용자는 "그대로네" 라고 느낀다. 반대로 0 을 요구하면 후보가 조금만 적어도 영원히 못
     * 만족해 시도 상한만 소모한다.
     */
    private static final double MAX_OVERLAP_RATIO = 0.5;

    private final CourseGenerationService courseGenerationService;
    private final RegionPoiService regionPoiService;
    private final UnroutableCoordinateService unroutableCoordinateService;

    /**
     * 직전 코스와 다른 코스를 만든다.
     *
     * <p>씨앗을 지정하지 않으면 <b>직전 씨앗에서 파생</b>해 고른다. 지정하면 그 씨앗만 쓰고 시도하지 않는다 —
     * <b>재현이 목적</b>인 호출이라 다르게 만들려고 씨앗을 바꿔 버리면 뜻이 뒤집힌다.
     *
     * @param command 첫 생성과 같은 조건. {@code excludePoiContentIds} 로 "이 장소 말고" 를 함께 준다
     * @param requestedSeed 씨앗을 직접 고르면 그 값, 맡기면 {@code null}
     * @param previousSeed 지금 화면에 떠 있는 코스의 씨앗. 없으면 첫 생성({@link GenerateCourse#FIRST_SEED})으로 본다
     */
    public RegeneratedCourse regenerate(GenerateCourse command, Long requestedSeed, Long previousSeed) {
        // 후보는 한 번만 모은다. collect 는 캐시가 없어 호출마다 TourAPI 를 세 번 부른다.
        RegionPois pois = regionPoiService.collect(command.regionId());
        // 차단 좌표도 한 번만 읽는다(#335). 씨앗 판정이 시도마다 selectedSightIds 를 부르는데,
        // 그 안에서 읽으면 한 요청 안에서 안 바뀌는 값을 시도 횟수만큼 다시 조회한다.
        Set<CoordinateKey> blocked = unroutableCoordinateService.blockedPoints();
        long previous = previousSeed != null ? previousSeed : GenerateCourse.FIRST_SEED;
        Set<String> previousSights =
                courseGenerationService.selectedSightIds(command.withSeed(previous), pois, blocked);

        long chosen = requestedSeed != null
                ? requestedSeed
                : seedMostDifferentFrom(command, pois, previousSights, previous, blocked);

        // 외부를 타는 조립은 여기 한 번뿐이다 — 보통 생성 한 번과 같은 비용이다.
        GeneratedCourse built = courseGenerationService.generate(command.withSeed(chosen), pois);
        double overlap = overlapRatio(
                courseGenerationService.selectedSightIds(command.withSeed(chosen), pois, blocked), previousSights);
        boolean different = overlap < MAX_OVERLAP_RATIO;
        if (!different) {
            // degrade 는 info 로 남긴다 — 응답까지 전달되는 사용자 가시 결과라 요청 완료 줄이 대신해주지 못한다.
            log.info("재생성이 충분히 다른 코스를 못 만들었습니다 — 후보 부족 overlap={} seed={}", overlap, chosen);
        }
        return new RegeneratedCourse(built, chosen, different, overlap);
    }

    /**
     * 직전 코스와 가장 덜 겹치는 씨앗을 고른다 — <b>좌표 계산만</b> 한다.
     *
     * <p>충분히 다른 것을 찾으면 바로 멈춘다. 다 돌아도 못 찾으면 그중 가장 덜 겹치는 것을 쓴다 — 후보가 모자란
     * 지역에서도 무언가는 내려야 하고, 달라지지 않았다는 사실은 응답이 따로 알린다.
     *
     * <p><b>후보 씨앗은 직전 씨앗에서 파생한다({@link SplittableRandom}).</b> 전역 난수를 쓰면 같은 요청이 매번
     * 다른 답을 낸다. 그러면 FE 가 네트워크 재시도로 같은 요청을 두 번 보냈을 때 화면이 이유 없이 바뀌고,
     * 무엇보다 <b>이 동작을 검증하는 테스트가 확률적으로만 통과한다</b> — 실제로 CI 에서 간헐 실패했다.
     *
     * <p>파생이라도 다양성은 그대로다. "다시 추천받기" 는 매번 <b>직전 응답의 씨앗</b>을 넘기므로 씨앗이 계속
     * 바뀌고, 그때마다 새 후보열이 나온다. 달라지는 축은 난수가 아니라 직전 씨앗이다.
     */
    private long seedMostDifferentFrom(
            GenerateCourse command, RegionPois pois, Set<String> previousSights, long previous,
            Set<CoordinateKey> blocked) {
        RandomGenerator random = new SplittableRandom(previous);
        long bestSeed = GenerateCourse.FIRST_SEED;
        double bestOverlap = Double.MAX_VALUE;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            long seed = random.nextLong();
            double overlap = overlapRatio(
                    courseGenerationService.selectedSightIds(command.withSeed(seed), pois, blocked), previousSights);
            if (overlap < bestOverlap) {
                bestSeed = seed;
                bestOverlap = overlap;
            }
            if (overlap < MAX_OVERLAP_RATIO) {
                return seed;
            }
        }
        return bestSeed;
    }

    /** 직전 코스 대비 겹친 비율. 직전이 비어 있으면 비교할 것이 없으므로 완전히 다른 것으로 본다. */
    private static double overlapRatio(Set<String> places, Set<String> previousPlaces) {
        if (previousPlaces.isEmpty()) {
            return 0;
        }
        long shared = places.stream().filter(previousPlaces::contains).count();
        return (double) shared / previousPlaces.size();
    }
}
