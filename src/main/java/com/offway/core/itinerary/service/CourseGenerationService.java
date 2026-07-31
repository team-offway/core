package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseNeeds;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.GeoCluster;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.RouteOptimizer;
import com.offway.core.transport.service.RouteTimeProvider;
import com.offway.core.transport.service.TrainAccessService;
import com.offway.core.transport.service.TravelTimeProvider;
import com.offway.core.transport.service.dto.TrainAccess;
import com.offway.core.trip.service.RegionPoiService;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.service.dto.RegionPois;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.weather.service.WeatherService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 코스 자동 생성(course-logic 9단계) — 조율만 한다. POI 는 trip({@link RegionPoiService}), 동선·이동시간은
 * transport({@link TravelTimeProvider}), 혜택은 policy({@link PolicyService}) 에서 얻고, 슬롯 배치·조립은
 * itinerary 도메인({@link Course}·{@link Slot})으로 표현한다. 외부(TourAPI) 호출은 trip service 안에서 tx 밖에 끝난다.
 *
 * <p>⑦ 동선: 방문 순서는 직선거리 최근접(대량 O(n²)이라 근사), 이웃 구간의 <b>실제 이동시간은 자차 기준 TMAP 실측</b>
 * ({@link RouteTimeProvider}, 키·한도 불가 시 직선거리 폴백)으로 채운다. 대중교통 실이동(버스·기차)은 #26·#27 연동 뒤.
 * <b>Interim</b>: ② POI 랭킹은 TourAPI 정렬 순서(관광빅데이터가 지역 단위라 POI 별 방문자 랭킹 부재), ③ 평일오픈 필터는 후속.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseGenerationService {

    private final RegionPoiService regionPoiService;
    private final TravelTimeProvider travelTimeProvider;
    private final RouteTimeProvider routeTimeProvider;
    private final RouteOptimizer routeOptimizer;
    private final PolicyService policyService;
    private final WeatherService weatherService;
    private final RegionRepository regionRepository;
    private final TrainAccessService trainAccessService;

    public GeneratedCourse generate(GenerateCourse command) {
        // ① POI 수집 (trip)
        return generate(command, regionPoiService.collect(command.regionId()));
    }

    /**
     * 이미 모은 후보로 코스를 짠다 — 재생성이 씨앗을 바꿔가며 시도할 때 <b>후보를 다시 모으지 않게</b> 한다(#114).
     *
     * <p>{@code collect} 는 캐시가 없어 호출마다 TourAPI 를 세 번 부른다. 시도마다 다시 모으면 그 배수만큼
     * 외부 호출이 는다.
     */
    public GeneratedCourse generate(GenerateCourse command, RegionPois pois) {

        // ①' "이 장소 말고" — 재생성이 지정한 장소를 후보에서 뺀다(#114). 빈 집합이면 그대로다.
        List<PoiCandidate> sightPool = exclude(pois.sights(), command.excludePoiContentIds());
        List<PoiCandidate> foodPool = exclude(pois.foods(), command.excludePoiContentIds());
        List<PoiCandidate> stayPool = exclude(pois.stays(), command.excludePoiContentIds());

        // ④ 필요 개수 (밀도×일수)
        CourseNeeds needs = CourseNeeds.of(command.density(), command.travelDays());
        if (sightPool.isEmpty()) {
            throw ItineraryException.courseNotBuildable(); // 볼거리가 없으면 코스가 아니다(식사만 있는 코스 방지)
        }

        // ⑤ 지리 클러스터링: 흩어진 후보 대신 밀집한 볼거리를 고르고(아웃라이어 배제), 맛집·숙소는 그 코스 중심 근처로 →
        // 순서 최적화만으로는 못 줄이는 이동시간을 선택 단계에서 줄인다.
        // 씨앗이 다르면 다른 군집이 잡혀 코스가 달라지지만, 뭉치는 성질은 그대로라 동선이 망가지지 않는다(#114).
        List<PoiCandidate> sights =
                reorder(sightPool, GeoCluster.selectCompact(coords(sightPool), needs.sights(), seedIndexOf(command)));
        Coordinate hub = GeoCluster.centroid(coords(sights));
        List<PoiCandidate> foods = reorder(foodPool, GeoCluster.nearest(coords(foodPool), hub, needs.foods()));
        List<PoiCandidate> stays = reorder(stayPool, GeoCluster.nearest(coords(stayPool), hub, needs.stays()));

        // ⑤⑦ interim: 출발지 기준 최근접 정렬(동선) → 하루씩 순서대로 슬라이스하면 가까운 곳끼리 묶인다
        List<PoiCandidate> orderedSights =
                nearestNeighborOrder(sights, command.transport(), command.originLat(), command.originLng());

        // ⑥ 슬롯 배치 → ⑨ 조립
        List<DaySchedule> days = buildDays(command, orderedSights, foods, stays);
        Course course = Course.of(command.regionId(), command.density(), command.transport(), days);

        // ⑧ 혜택 (policy)
        List<GeneratedCourse.Benefit> benefits = policyService.matchForRegion(command.regionId(), command.travelDate())
                .stream()
                .map(policy -> new GeneratedCourse.Benefit(policy.getId(), policy.getType(), policy.badgeText()))
                .toList();

        // 여행 날짜의 코스 지역 날씨 — 코스 중심(hub) 좌표로 조회. 부가 정보라 미조회·실패·예보범위 밖이면 null.
        DailyWeather weather =
                weatherService.dailyWeather(hub.lat(), hub.lng(), command.travelDate()).orElse(null);

        // 대중교통 코스면 출발지→지역 열차 접근 조회(자차는 TMAP 실측이라 불필요). 부가 정보라 실패해도 코스는 그대로.
        TrainAccess trainAccess = command.transport() == TransportMode.TRANSIT ? trainAccessFor(command) : null;

        log.info("코스 생성 regionId={} days={} slots={} benefits={} weather={} trainAccess={}",
                command.regionId(), course.getTravelDays(), course.totalSlots(), benefits.size(),
                weather != null, trainAccess != null ? trainAccess.status() : "N/A");
        return new GeneratedCourse(course, benefits, weather, trainAccess);
    }

    /**
     * 이 씨앗이면 어떤 볼거리가 뽑히는지 — <b>외부 호출 없이</b> 좌표 계산만으로 답한다(#114).
     *
     * <p>재생성이 "충분히 다른가" 를 판정할 때 쓴다. 판정하자고 코스를 통째로 짜면 TMAP 경유지 최적화·이동시간·
     * 날씨·열차 조회가 시도 횟수만큼 곱해진다 — TMAP 경유지 최적화는 <b>일일 허용량이 50건</b>이라 요청 한 번이
     * 그날 몫을 태울 수 있다.
     *
     * <p>순서는 담지 않는다. 사용자가 "다른 코스" 로 느끼는 것은 <b>어디를 가느냐</b>이지 순서가 아니다.
     */
    public Set<String> selectedSightIds(GenerateCourse command, RegionPois pois) {
        List<PoiCandidate> pool = exclude(pois.sights(), command.excludePoiContentIds());
        if (pool.isEmpty()) {
            return Set.of();
        }
        CourseNeeds needs = CourseNeeds.of(command.density(), command.travelDays());
        return reorder(pool, GeoCluster.selectCompact(coords(pool), needs.sights(), seedIndexOf(command))).stream()
                .map(PoiCandidate::contentId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * 씨앗값을 후보 인덱스로 접는다. {@code GeoCluster} 가 후보 수로 다시 나머지를 취하므로 여기서는 부호만 없앤다.
     *
     * <p>씨앗을 인덱스로 <b>그대로</b> 쓰는 이유 — 해시를 한 번 더 섞으면 "seed 를 1 올리면 옆 후보" 라는 성질이
     * 사라져, 문의 대응할 때 무슨 일이 있었는지 재구성하기 어려워진다.
     */
    private static int seedIndexOf(GenerateCourse command) {
        return (int) Math.floorMod(command.seed(), Integer.MAX_VALUE);
    }

    /** "이 장소 말고" — 제외 목록에 든 후보를 뺀다. 빈 목록이면 원본 그대로다. */
    private static List<PoiCandidate> exclude(List<PoiCandidate> pool, Set<String> excluded) {
        if (excluded.isEmpty()) {
            return pool;
        }
        return pool.stream().filter(poi -> !excluded.contains(poi.contentId())).toList();
    }

    /** 대중교통 코스의 출발지→지역 열차 접근. 출발·지역 좌표의 최근접 역으로 해석한다. */
    private TrainAccess trainAccessFor(GenerateCourse command) {
        return regionRepository.findByIds(List.of(command.regionId())).stream()
                .findFirst()
                .map(region -> trainAccessService.accessTo(
                        command.originLat(), command.originLng(),
                        region.getLat(), region.getLng(), command.travelDate()))
                .orElse(null);
    }

    /** 출발지에서 가장 가까운 곳부터 이어붙이는 그리디 정렬(하루 묶기용). 하루 내부 순서는 TMAP 경유지 최적화로 다시 다듬는다. */
    private List<PoiCandidate> nearestNeighborOrder(
            List<PoiCandidate> pois, TransportMode transport, double originLat, double originLng) {
        List<PoiCandidate> remaining = new ArrayList<>(pois);
        List<PoiCandidate> ordered = new ArrayList<>();
        Coordinate current = new Coordinate(originLat, originLng);
        while (!remaining.isEmpty()) {
            Coordinate from = current;
            PoiCandidate next = remaining.stream()
                    .min(Comparator.comparingInt(poi -> travelMinutes(from, poi, transport)))
                    .orElseThrow();
            ordered.add(next);
            remaining.remove(next);
            current = coord(next);
        }
        return ordered;
    }

    /** 볼거리를 하루씩 나눠 슬롯으로 배치한다. 장소가 부족하면 채워지는 날까지만(일차는 1부터 연속으로 다시 매긴다). */
    private List<DaySchedule> buildDays(
            GenerateCourse command, List<PoiCandidate> sights, List<PoiCandidate> foods, List<PoiCandidate> stays) {
        int perDaySights = command.density().sightsPerDay();
        List<DaySchedule> days = new ArrayList<>();
        int si = 0;
        int fi = 0;
        int sti = 0;
        for (int day = 1; day <= command.travelDays(); day++) {
            List<PoiCandidate> daySights = slice(sights, si, perDaySights);
            si += daySights.size();
            if (command.transport() == TransportMode.CAR) {
                // 하루 볼거리 순서를 실도로 기준 최적화(자차). 대중교통은 #26·#27 전까지 근사 순서 유지.
                daySights = reorder(daySights, routeOptimizer.optimalOrder(coords(daySights)));
            }
            List<PoiCandidate> dayFoods = slice(foods, fi, 2);
            fi += dayFoods.size();
            boolean lastDay = day == command.travelDays();
            PoiCandidate stay = (!lastDay && sti < stays.size()) ? stays.get(sti++) : null;

            List<Slot> slots = arrangeDay(daySights, dayFoods, stay, command.transport());
            if (!slots.isEmpty()) {
                days.add(DaySchedule.of(days.size() + 1, slots)); // 빈 날은 건너뛰고 1부터 연속 번호
            }
        }
        if (days.isEmpty()) {
            throw ItineraryException.courseNotBuildable();
        }
        return days;
    }

    /** 하루를 오전관광→점심→오후관광→저녁→(숙박) 순서로 배치하고 슬롯간 이동시간을 채운다. */
    private List<Slot> arrangeDay(
            List<PoiCandidate> sights, List<PoiCandidate> foods, PoiCandidate stay, TransportMode transport) {
        List<Entry> entries = new ArrayList<>();
        int half = (sights.size() + 1) / 2;
        for (int i = 0; i < sights.size(); i++) {
            entries.add(new Entry(SlotKind.SIGHT, i < half ? TimeOfDay.MORNING : TimeOfDay.AFTERNOON, sights.get(i)));
        }
        // 점심은 오전 관광 뒤, 저녁은 오후 관광 뒤에 끼운다
        if (!foods.isEmpty()) {
            entries.add(half, new Entry(SlotKind.FOOD, TimeOfDay.LUNCH, foods.get(0)));
        }
        if (foods.size() >= 2) {
            entries.add(new Entry(SlotKind.FOOD, TimeOfDay.DINNER, foods.get(1)));
        }
        if (stay != null) {
            entries.add(new Entry(SlotKind.STAY, TimeOfDay.DINNER, stay));
        }

        List<Slot> slots = new ArrayList<>();
        Coordinate prev = null;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int travel = prev == null ? 0 : legMinutes(prev, coord(e.poi()), transport);
            slots.add(Slot.of(i + 1, e.timeOfDay(), e.kind(), e.poi().contentId(), e.poi().title(),
                    e.poi().lat(), e.poi().lng(), travel,
                    e.poi().imageUrl(), e.poi().address(), e.poi().catchphrase()));
            prev = coord(e.poi());
        }
        return slots;
    }

    private int travelMinutes(Coordinate from, PoiCandidate to, TransportMode transport) {
        return travelTimeProvider.reachMinutes(from, coord(to), transport);
    }

    /**
     * 이웃 슬롯 간 이동시간 — 자차는 TMAP 실측(불가 시 직선거리 폴백), 대중교통은 직선거리 근사(#26·#27 연동 전까지). 최근접
     * 정렬(대량 O(n²))은 계속 직선거리를 쓰고, TMAP 실측은 여기(하루 이웃 구간 소수)에서만 호출해 한도를 지킨다.
     */
    private int legMinutes(Coordinate from, Coordinate to, TransportMode transport) {
        return transport == TransportMode.CAR
                ? routeTimeProvider.drivingMinutes(from, to)
                : travelTimeProvider.reachMinutes(from, to, transport);
    }

    private static Coordinate coord(PoiCandidate poi) {
        return new Coordinate(poi.lat(), poi.lng());
    }

    private static List<Coordinate> coords(List<PoiCandidate> pois) {
        return pois.stream().map(CourseGenerationService::coord).toList();
    }

    /** 최적화가 돌려준 인덱스 순서로 POI 를 재배열한다. */
    private static List<PoiCandidate> reorder(List<PoiCandidate> pois, List<Integer> order) {
        return order.stream().map(pois::get).toList();
    }

    private static <T> List<T> slice(List<T> list, int from, int count) {
        if (from >= list.size()) {
            return List.of();
        }
        return list.subList(from, Math.min(list.size(), from + count));
    }

    /** 배치 항목 — 슬롯 종류·시간대·장소. */
    private record Entry(SlotKind kind, TimeOfDay timeOfDay, PoiCandidate poi) {
    }
}
