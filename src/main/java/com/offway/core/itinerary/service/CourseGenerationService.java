package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseNeeds;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.DayStart;
import com.offway.core.itinerary.domain.GeoCluster;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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

        // 지역은 날씨·열차 접근 양쪽이 쓴다 — 한 번만 읽는다(#129).
        Region region = regionRepository.findByIds(List.of(command.regionId())).stream().findFirst().orElse(null);

        // 대중교통이면 출발지→지역 열차 접근을 여기서 조회한다(자차는 TMAP 실측이라 불필요). 부가 정보라 실패해도 코스는 그대로.
        // 슬롯 배치보다 앞서야 한다 — 동선의 기준점과 1일차 시작 시간대가 이 결과에서 나온다(#127).
        TrainAccess trainAccess =
                command.transport() == TransportMode.TRANSIT ? trainAccessFor(command, region) : null;

        // ⑤⑦ interim: 기준점 최근접 정렬(동선) → 하루씩 순서대로 슬라이스하면 가까운 곳끼리 묶인다
        List<PoiCandidate> orderedSights =
                nearestNeighborOrder(sights, command.transport(), regionAnchor(command, trainAccess));

        // ⑥ 슬롯 배치 → ⑨ 조립. 첫날은 도착 시각 이후 남는 시간대만 쓴다.
        List<DaySchedule> days = buildDays(command, firstDayStart(command, trainAccess), orderedSights, foods, stays);
        Course course = Course.of(command.regionId(), command.density(), command.transport(), days, command.travelDate());

        // ⑧ 혜택 (policy)
        List<GeneratedCourse.Benefit> benefits = policyService.matchForRegion(command.regionId(), command.travelDate())
                .stream()
                .map(policy -> new GeneratedCourse.Benefit(policy.getId(), policy.getType(), policy.badgeText()))
                .toList();

        // 코스 지역 날씨를 Day 마다 따로 — 2박3일이면 날마다 다르다. 첫날 것으로 코스 전체를 대표하면 이튿날이 틀린다(#141).
        // 코스 중심(hub) 좌표로 조회하고, 시도·시군구를 함께 넘긴다: 나흘 뒤부터는 좌표 격자가 아니라
        // 광역 구역 단위 중기예보가 답한다(#129). 부가 정보라 미조회·실패·예보범위 밖인 Day 는 그냥 빈다.
        Map<Integer, DailyWeather> weatherByDay = weatherByDay(command, course, region, hub);

        log.info("코스 생성 regionId={} days={} slots={} benefits={} weatherDays={} trainAccess={}",
                command.regionId(), course.getTravelDays(), course.totalSlots(), benefits.size(),
                weatherByDay.size(), trainAccess != null ? trainAccess.status() : "N/A");
        return new GeneratedCourse(
                course, benefits, weatherByDay, trainAccess, region == null ? null : region.getSigungu());
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

    /**
     * Day 별 날씨. 예보가 없는 Day 는 <b>키 자체를 넣지 않는다</b> — 화면이 Day 마다 독립적으로 판단한다.
     *
     * <p>날짜를 모르는 코스(저장 시 날짜 미입력)는 물어볼 기준이 없어 통째로 빈다.
     */
    private Map<Integer, DailyWeather> weatherByDay(
            GenerateCourse command, Course course, Region region, Coordinate hub) {
        if (command.travelDate() == null) {
            return Map.of();
        }
        Map<Integer, DailyWeather> byDay = new LinkedHashMap<>();
        for (int dayNumber = 1; dayNumber <= course.getTravelDays(); dayNumber++) {
            // 키는 반드시 그 Day 번호다 — 채워진 개수로 매기면 예보 없는 Day 를 건너뛸 때 뒤 Day 가 앞으로 밀린다.
            int day = dayNumber;
            weatherService.dailyWeather(
                            hub.lat(), hub.lng(),
                            region == null ? null : region.getSido(),
                            region == null ? null : region.getSigungu(),
                            Course.dateOfDay(command.travelDate(), day))
                    .ifPresent(weather -> byDay.put(day, weather));
        }
        return byDay;
    }

    /** 대중교통 코스의 출발지→지역 열차 접근. 출발·지역 좌표의 최근접 역으로 해석한다. */
    private TrainAccess trainAccessFor(GenerateCourse command, Region region) {
        if (region == null) {
            return null;
        }
        return trainAccessService.accessTo(
                command.originLat(), command.originLng(),
                region.getLat(), region.getLng(), command.travelDate());
    }

    /**
     * 지역 안 동선의 기준점 — <b>대중교통은 내린 역, 자차는 출발지</b>(#127).
     *
     * <p>서울→경주 KTX 인데 집 좌표를 기준으로 잡으면 "경주 장소들 중 서울에서 직선거리로 가까운 곳" 부터 이어붙는다.
     * 실제로는 경주역에 내려 거기서 움직이므로 동선이 반대로 짜인다.
     *
     * <p>역이 없거나(오지) 접근 조회가 실패해 도착 지점을 모르면 출발지로 되돌아간다 — 이전과 같은 동작이라 회귀가 없다.
     */
    private static Coordinate regionAnchor(GenerateCourse command, TrainAccess trainAccess) {
        Coordinate origin = new Coordinate(command.originLat(), command.originLng());
        if (trainAccess == null) {
            return origin;
        }
        return trainAccess.arrivalPoint().orElse(origin);
    }

    /**
     * 여행 첫날 가용 시간대 — 도착 시각을 <b>알 때만</b> 줄인다(#127).
     *
     * <p>자차·역 없음·그날 운행 없음·조회 실패는 전부 하루 전부다. 모르는 것을 늦은 도착으로 단정하면 조회 실패가 조용히
     * 코스를 깎는다 — degrade 가 정상처럼 보이는 최악의 형태다.
     */
    private static DayStart firstDayStart(GenerateCourse command, TrainAccess trainAccess) {
        if (trainAccess == null) {
            return DayStart.fullDay();
        }
        return trainAccess.arrivalAt()
                .map(arriveAt -> firstDayStart(command.travelDate(), arriveAt))
                .orElseGet(DayStart::fullDay);
    }

    private static DayStart firstDayStart(LocalDate travelDate, LocalDateTime arriveAt) {
        if (travelDate != null && arriveAt.toLocalDate().isAfter(travelDate)) {
            // 자정을 넘겨 닿는다 — 첫날은 통째로 이동이다. 시각만 보면 새벽 도착이 "오전부터 여유" 로 둔갑한다.
            return DayStart.none();
        }
        return DayStart.arrivingAt(arriveAt.toLocalTime());
    }

    /** 기준점에서 가장 가까운 곳부터 이어붙이는 그리디 정렬(하루 묶기용). 하루 내부 순서는 TMAP 경유지 최적화로 다시 다듬는다. */
    private List<PoiCandidate> nearestNeighborOrder(
            List<PoiCandidate> pois, TransportMode transport, Coordinate anchor) {
        List<PoiCandidate> remaining = new ArrayList<>(pois);
        List<PoiCandidate> ordered = new ArrayList<>();
        Coordinate current = anchor;
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

    /**
     * 볼거리를 하루씩 나눠 슬롯으로 배치한다. 장소가 부족하면 채워지는 날까지만(일차는 1부터 연속으로 다시 매긴다).
     *
     * <p>첫날만 {@code firstDayStart} 로 시간대가 좁아질 수 있다(#127). 좁아진 만큼 <b>덜 잘라 쓰므로</b> 남은
     * 후보는 사라지지 않고 그대로 이튿날 몫이 된다.
     */
    private List<DaySchedule> buildDays(GenerateCourse command, DayStart firstDayStart,
            List<PoiCandidate> sights, List<PoiCandidate> foods, List<PoiCandidate> stays) {
        int perDaySights = command.density().sightsPerDay();
        List<DaySchedule> days = new ArrayList<>();
        int si = 0;
        int fi = 0;
        int sti = 0;
        for (int day = 1; day <= command.travelDays(); day++) {
            DayStart start = day == 1 ? firstDayStart : DayStart.fullDay();
            List<PoiCandidate> daySights = slice(sights, si, start.sightCapacity(perDaySights));
            si += daySights.size();
            if (command.transport() == TransportMode.CAR) {
                // 하루 볼거리 순서를 실도로 기준 최적화(자차). 대중교통은 #26·#27 전까지 근사 순서 유지.
                daySights = reorder(daySights, routeOptimizer.optimalOrder(coords(daySights)));
            }
            List<PoiCandidate> dayFoods = slice(foods, fi, start.mealCapacity());
            fi += dayFoods.size();
            boolean lastDay = day == command.travelDays();
            PoiCandidate stay = (!lastDay && sti < stays.size()) ? stays.get(sti++) : null;

            List<Slot> slots = arrangeDay(daySights, dayFoods, stay, command.transport(), start);
            if (!slots.isEmpty()) {
                // 표시 번호는 1부터 연속(빈 날은 건너뛴다), 날짜 계산용 오프셋은 달력을 그대로 따른다.
                // 둘을 겸하면 첫날이 빌 때 날짜와 날씨가 하루 앞당겨진다(#159).
                days.add(DaySchedule.of(days.size() + 1, day - 1, slots));
            }
        }
        if (days.isEmpty()) {
            throw ItineraryException.courseNotBuildable();
        }
        return days;
    }

    /**
     * 하루를 오전관광→점심→오후관광→저녁→(숙박) 순서로 배치하고 슬롯간 이동시간을 채운다.
     *
     * <p>{@code start} 가 좁으면 이미 지난 시간대는 비운다 — 오전을 못 쓰면 볼거리가 전부 오후로 간다. <b>숙박은
     * 예외로 시간대 판정을 타지 않는다</b> — 밤늦게 닿아도 잘 곳은 필요하다.
     */
    private List<Slot> arrangeDay(List<PoiCandidate> sights, List<PoiCandidate> foods, PoiCandidate stay,
            TransportMode transport, DayStart start) {
        List<Entry> entries = new ArrayList<>();
        int morning = start.morningShare(sights.size());
        for (int i = 0; i < sights.size(); i++) {
            entries.add(
                    new Entry(SlotKind.SIGHT, i < morning ? TimeOfDay.MORNING : TimeOfDay.AFTERNOON, sights.get(i)));
        }
        // 점심은 오전 관광 뒤, 저녁은 오후 관광 뒤에 끼운다
        int fi = 0;
        if (start.allows(TimeOfDay.LUNCH) && fi < foods.size()) {
            entries.add(morning, new Entry(SlotKind.FOOD, TimeOfDay.LUNCH, foods.get(fi++)));
        }
        if (start.allows(TimeOfDay.DINNER) && fi < foods.size()) {
            entries.add(new Entry(SlotKind.FOOD, TimeOfDay.DINNER, foods.get(fi++)));
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
