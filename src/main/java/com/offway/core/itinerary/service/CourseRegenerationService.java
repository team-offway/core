package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.itinerary.service.dto.RegeneratedCourse;
import java.util.LinkedHashSet;
import java.util.Set;
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
     * 새 씨앗을 몇 번까지 시도할지. 후보가 적은 지역에서 무한정 돌면 재생성이 그만큼 느려진다 — 사용자가 기다리는
     * 버튼이라 <b>다양성보다 응답 속도가 먼저다.</b> 시도마다 붙는 비용은 좌표 계산뿐이고(POI 는 캐시에서 온다)
     * 외부 호출은 늘지 않는다.
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

    /**
     * 직전 코스와 다른 코스를 만든다.
     *
     * <p>씨앗을 지정하지 않으면 무작위로 고른다. 지정하면 그 씨앗만 쓰고 시도하지 않는다 — <b>재현이 목적</b>인
     * 호출이라 다르게 만들려고 씨앗을 바꿔 버리면 뜻이 뒤집힌다.
     *
     * @param command 첫 생성과 같은 조건. {@code excludePoiContentIds} 로 "이 장소 말고" 를 함께 준다
     * @param requestedSeed 씨앗을 직접 고르면 그 값, 맡기면 {@code null}
     * @param previousSeed 지금 화면에 떠 있는 코스의 씨앗. 없으면 첫 생성({@link GenerateCourse#FIRST_SEED})으로 본다
     */
    public RegeneratedCourse regenerate(GenerateCourse command, Long requestedSeed, Long previousSeed) {
        long previous = previousSeed != null ? previousSeed : GenerateCourse.FIRST_SEED;
        Set<String> previousPlaces = placesOf(courseGenerationService.generate(command.withSeed(previous)));

        if (requestedSeed != null) {
            GeneratedCourse fixed = courseGenerationService.generate(command.withSeed(requestedSeed));
            return result(fixed, requestedSeed, previousPlaces);
        }

        RandomGenerator random = RandomGenerator.getDefault();
        GeneratedCourse best = null;
        long bestSeed = previous;
        // 겹침이 완전히 1.0 인 경우(후보가 모자라 늘 같은 코스)에도 첫 시도가 best 가 되도록 상한 밖에서 시작한다.
        double bestOverlap = Double.MAX_VALUE;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            long seed = random.nextLong();
            GeneratedCourse candidate = courseGenerationService.generate(command.withSeed(seed));
            double overlap = overlapRatio(placesOf(candidate), previousPlaces);
            if (overlap < bestOverlap) {
                best = candidate;
                bestSeed = seed;
                bestOverlap = overlap;
            }
            if (overlap < MAX_OVERLAP_RATIO) {
                break;
            }
        }

        if (best == null) {
            // MAX_ATTEMPTS 가 1 이상이면 첫 시도가 곧 best 다 — 여기 닿으면 불변식이 깨진 것이다.
            throw new IllegalStateException("재생성 시도가 하나도 결과를 남기지 않았습니다");
        }
        return result(best, bestSeed, previousPlaces);
    }

    private RegeneratedCourse result(GeneratedCourse course, long seed, Set<String> previousPlaces) {
        double overlap = overlapRatio(placesOf(course), previousPlaces);
        boolean different = overlap < MAX_OVERLAP_RATIO;
        if (!different) {
            log.info("재생성이 충분히 다른 코스를 못 만들었습니다 — 후보 부족 overlap={} seed={}", overlap, seed);
        }
        return new RegeneratedCourse(course, seed, different, overlap);
    }

    /**
     * 코스가 담은 <b>볼거리</b>들. 순서가 아니라 구성이 달라져야 사용자가 "다른 코스" 로 느낀다.
     *
     * <p><b>맛집·숙소는 세지 않는다.</b> 그 지역에 맛집이 네 곳뿐이면 어떤 코스를 짜도 같은 네 곳이 나온다 — 함께
     * 세면 볼거리를 전부 바꿔도 "안 달라졌다" 가 되어 판정이 사용자 체감과 어긋난다. 다양성 축(씨앗)이 실제로
     * 작용하는 곳도 볼거리 선택이고, 맛집·숙소는 그렇게 정해진 코스 중심에서 파생될 뿐이다.
     */
    private static Set<String> placesOf(GeneratedCourse generated) {
        Course course = generated.course();
        Set<String> places = new LinkedHashSet<>();
        for (DaySchedule day : course.getDays()) {
            for (Slot slot : day.getSlots()) {
                if (slot.getKind() == SlotKind.SIGHT) {
                    places.add(slot.getPoiContentId());
                }
            }
        }
        return places;
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
