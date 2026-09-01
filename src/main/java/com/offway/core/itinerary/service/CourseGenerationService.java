package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseNeeds;
import com.offway.core.itinerary.domain.CandidatePool;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.DayStart;
import com.offway.core.itinerary.domain.GeoCluster;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.RouteOptimizer;
import com.offway.core.transport.service.RouteTimeProvider;
import com.offway.core.transport.service.RegionAccessService;
import com.offway.core.transport.service.UnroutableCoordinateService;
import com.offway.core.transport.service.TravelTimeProvider;
import com.offway.core.transport.service.dto.RegionAccess;
import com.offway.core.trip.service.RegionPoiService;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.service.dto.RegionPois;
import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
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
    private final CourseWeatherProvider courseWeatherProvider;
    private final OpeningHoursProvider openingHoursProvider;
    private final FestivalPeriodProvider festivalPeriodProvider;
    private final RegionRepository regionRepository;
    private final RegionAccessService regionAccessService;
    private final UnroutableCoordinateService unroutableCoordinateService;

    public GeneratedCourse generate(GenerateCourse command) {
        // ① POI 수집 (trip)
        return generate(command, regionPoiService.collect(command.regionId(), command.travelDate()));
    }

    /**
     * 이미 모은 후보로 코스를 짠다 — 재생성이 씨앗을 바꿔가며 시도할 때 <b>후보를 다시 모으지 않게</b> 한다(#114).
     *
     * <p>{@code collect} 는 캐시가 없어 호출마다 TourAPI 를 세 번 부른다. 시도마다 다시 모으면 그 배수만큼
     * 외부 호출이 는다.
     */
    public GeneratedCourse generate(GenerateCourse command, RegionPois pois) {

        // ①' "이 장소 말고" — 재생성이 지정한 장소를 후보에서 뺀다(#114). 빈 집합이면 그대로다.
        // ①'' 경로를 못 만드는 좌표를 빼고(#335), 같은 좌표는 풀마다 하나만 남긴다.
        Set<CoordinateKey> blocked = unroutableCoordinateService.blockedPoints();
        List<PoiCandidate> sightPool = usable(pois.sights(), command, blocked);
        List<PoiCandidate> foodPool = usable(pois.foods(), command, blocked);
        List<PoiCandidate> stayPool = usable(pois.stays(), command, blocked);

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

        // 지역까지 무엇을 타고 가서 어디에 닿는지. 부가 정보라 실패해도 코스는 그대로다.
        // 슬롯 배치보다 앞서야 한다 — 동선의 기준점과 1일차 시작 시간대가 이 결과에서 나온다(#127).
        // 자차도 만든다(#379) — 예전에는 여기서 null 이라 화면의 교통 카드가 통째로 비었다.
        RegionAccess regionAccess = regionAccessFor(command, region);

        // ⑤⑦ interim: 기준점 최근접 정렬(동선) → 하루씩 순서대로 슬라이스하면 가까운 곳끼리 묶인다
        List<PoiCandidate> orderedSights =
                nearestNeighborOrder(sights, command.transport(), regionAnchor(command, regionAccess));

        // ⑥ 슬롯 배치 → ⑨ 조립. 첫날은 도착 시각 이후 남는 시간대만 쓴다.
        List<DaySchedule> days =
                buildDays(command, firstDayStart(command, regionAccess), orderedSights, foods, stays);
        // 기간은 days.size() 가 아니라 **요청한 일수**다. 일정이 없는 날은 코스에서 빠지므로(#159) 둘이 갈린다 —
        // 첫날이 이동뿐이어도 그날은 여행 중이고, 연차도 그만큼 나간다(#164).
        Course course = Course.of(
                command.regionId(), command.density(), command.transport(), days, command.travelDate(),
                command.travelDays(), command.startDayLeave());

        // ⑧ 혜택 (policy)
        List<GeneratedCourse.Benefit> benefits = policyService.matchForRegion(command.regionId(), command.travelDate())
                .stream()
                .map(policy -> new GeneratedCourse.Benefit(policy.getId(), policy.getType(), policy.badgeText()))
                .toList();

        // 코스 지역 날씨를 Day 마다 따로 — 2박3일이면 날마다 다르다. 첫날 것으로 코스 전체를 대표하면 이튿날이 틀린다(#141).
        // 코스 중심(hub) 좌표로 조회하고, 시도·시군구를 함께 넘긴다: 나흘 뒤부터는 좌표 격자가 아니라
        // 광역 구역 단위 중기예보가 답한다(#129). 부가 정보라 미조회·실패·예보범위 밖인 Day 는 그냥 빈다.
        Map<Integer, DailyWeather> weatherByDay = courseWeatherProvider.byDay(course, region, hub);

        log.debug("코스 생성 regionId={} days={} slots={} benefits={} weatherDays={} regionAccess={}",
                command.regionId(), course.getTravelDays(), course.totalSlots(), benefits.size(),
                weatherByDay.size(), regionAccess != null ? regionAccess.status() : "N/A");
        // 공유 토큰은 저장한 코스에만 있다 — 아직 저장 전이라 null 이다(#143).
        return GeneratedCourse.builder()
                .course(course)
                .benefits(benefits)
                .weatherByDay(weatherByDay)
                .regionAccess(regionAccess)
                .regionName(region == null ? null : region.getSigungu())
                // 받아 둔 것만 읽는다 — 요청 경로에서 외부를 부르지 않는다(#157). 아직 없으면 그 줄이 빈다.
                .hoursByContentId(openingHoursProvider.forCourse(course))
                .festivalPeriodByContentId(festivalPeriodProvider.forCourse(course))
                .build();
    }

    /**
     * 이 씨앗이면 어떤 볼거리가 뽑히는지 — <b>외부 호출 없이</b> 좌표 계산만으로 답한다(#114).
     *
     * <p>재생성이 "충분히 다른가" 를 판정할 때 쓴다. 판정하자고 코스를 통째로 짜면 TMAP 경유지 최적화·이동시간·
     * 날씨·열차 조회가 시도 횟수만큼 곱해진다 — TMAP 경유지 최적화는 <b>일일 허용량이 50건</b>이라 요청 한 번이
     * 그날 몫을 태울 수 있다.
     *
     * <p>순서는 담지 않는다. 사용자가 "다른 코스" 로 느끼는 것은 <b>어디를 가느냐</b>이지 순서가 아니다.
     *
     * <p><b>{@link #generate} 와 같은 후보 필터를 쓴다</b>(#335). 여기만 거르지 않으면 판정이 실제 코스에
     * 없는 장소를 세어, "충분히 다르다" 는 답과 화면에 뜨는 코스가 어긋난다. 같은 좌표를 접는 규칙은 특히
     * 씨앗에 따라 <b>어느 것이 남는지가 달라지므로</b>, 여기서 빠지면 판정이 실제와 더 크게 벌어진다.
     *
     * <p>차단 좌표를 <b>인자로 받는</b> 이유는 이 메서드가 씨앗마다 불리기 때문이다. 안에서 읽으면 재생성
     * 한 번에 같은 조회가 시도 횟수만큼 반복된다 — 한 요청 안에서 안 바뀌는 값이다.
     */
    public Set<String> selectedSightIds(GenerateCourse command, RegionPois pois, Set<CoordinateKey> blocked) {
        List<PoiCandidate> pool = usable(pois.sights(), command, blocked);
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
     * 실제로 코스에 쓸 수 있는 후보만 — 재생성 제외(#114) → 경로 불가 좌표 제외 · 같은 좌표 접기(#335).
     *
     * <p>뒤 둘은 좌표만 보는 계산이라 {@link CandidatePool} 이 소유한다. 여기서는 후보 타입을 그 인덱스로
     * 되돌리기만 한다.
     *
     * <p><b>풀 사이에는 접지 않는다.</b> 시장 좌표에서 볼거리 하나와 맛집 하나가 함께 나오는 것은
     * 자연스럽고, 그때의 "이동 0분" 은 틀린 값이 아니라 사실이다.
     */
    private static List<PoiCandidate> usable(
            List<PoiCandidate> pool, GenerateCourse command, Set<CoordinateKey> blocked) {
        List<PoiCandidate> remaining = exclude(pool, command.excludePoiContentIds());
        return reorder(
                remaining,
                CandidatePool.usable(coords(remaining), point -> blocked.contains(CoordinateKey.of(point)),
                        command.seed()));
    }

    /** 대중교통 코스의 출발지→지역 열차 접근. 출발·지역 좌표의 최근접 역으로 해석한다. */
    private RegionAccess regionAccessFor(GenerateCourse command, Region region) {
        if (region == null) {
            return null;
        }
        if (command.transport() != TransportMode.TRANSIT) {
            // 생성 응답에는 출발지 이름이 없다(#382). 이름은 저장 요청으로 들어오고, 카드가 뜨는 곳도
            // 저장 코스 상세다 — 추천 화면에서는 이 카드를 감춘다. 없는 값을 지어내지 않는다.
            return regionAccessService.carAccessTo(
                    null, region.shortName(),
                    command.originLat(), command.originLng(), region.getLat(), region.getLng());
        }
        return regionAccessService.accessTo(
                command.originLat(), command.originLng(),
                region.getLat(), region.getLng(), command.travelDate(),
                command.startDayLeave().departureTime());
    }

    /**
     * 지역 안 동선의 기준점 — <b>대중교통은 내린 역, 자차는 출발지</b>(#127).
     *
     * <p>서울→경주 KTX 인데 집 좌표를 기준으로 잡으면 "경주 장소들 중 서울에서 직선거리로 가까운 곳" 부터 이어붙는다.
     * 실제로는 경주역에 내려 거기서 움직이므로 동선이 반대로 짜인다.
     *
     * <p>역이 없거나(오지) 접근 조회가 실패해 도착 지점을 모르면 출발지로 되돌아간다 — 이전과 같은 동작이라 회귀가 없다.
     *
     * <p><b>수단으로 가른다. 접근 값이 있느냐로 가르지 않는다.</b> #379 로 자차에도 접근 값이 생겼는데,
     * 그 값의 도착 지점은 지역 중심이다. 있고 없고로 가르면 자차 동선이 출발지가 아니라 지역 중심에서
     * 시작하도록 조용히 바뀐다 — 이 PR 이 건드리려던 것이 아니다.
     */
    private static Coordinate regionAnchor(GenerateCourse command, RegionAccess regionAccess) {
        Coordinate origin = new Coordinate(command.originLat(), command.originLng());
        if (command.transport() != TransportMode.TRANSIT || regionAccess == null) {
            return origin;
        }
        return regionAccess.arrivalPoint().orElse(origin);
    }

    /**
     * 여행 첫날 가용 시간대 — 도착 시각을 <b>알 때만</b> 줄인다(#127·#138).
     *
     * <p><b>자차도 줄인다.</b> 예전에는 자차를 하루 전부로 뒀는데, 그러면 반반차로 15시에 나서는 사용자에게 오전
     * 볼거리를 넣는 코스가 나온다. 자차는 시간표가 없어 오히려 계산이 단순하다 — 출발 시각 + 이동시간이 도착
     * 시각이다.
     *
     * <p>역 없음·그날 운행 없음·조회 실패는 여전히 하루 전부다. 모르는 것을 늦은 도착으로 단정하면 조회 실패가
     * 조용히 코스를 깎는다 — degrade 가 정상처럼 보이는 최악의 형태다.
     */
    private DayStart firstDayStart(GenerateCourse command, RegionAccess regionAccess) {
        if (regionAccess == null) {
            return DayStart.fullDay(); // 지역 좌표를 모른다 — 도착 시각의 기준점이 없다
        }
        // 버스·여객선은 시간표를 못 물어 실제 편이 없다 — 대신 저장해 둔 소요시간을 출발 시각에 얹는다(#107).
        // 자차도 같은 계산이다(#379). 예전에는 이 자리에 자차 전용 경로가 따로 있었는데, 둘이 같은 식을
        // 각자 쓰고 있어 한쪽만 고치면 조용히 갈라졌다.
        return regionAccess.arrivalAt(command.travelDate(), command.startDayLeave().departureTime())
                .map(arriveAt -> DayStart.afterArriving(command.travelDate(), arriveAt))
                .orElseGet(DayStart::fullDay);
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
        fillDayGaps(days, command.transport());
        return days;
    }

    /**
     * 날짜가 바뀌는 구간의 이동시간을 채운다(#188) — 전날 마지막 장소(보통 숙소)에서 이날 첫 장소까지.
     *
     * <p>슬롯 사이와 <b>같은 방식</b>으로 잰다. 한 화면에서 12분(실도로)과 45분(직선 근사)이 섞여 나가면
     * 사용자가 둘을 같은 정밀도로 읽는다.
     *
     * <p>호출은 코스당 <b>여행일수 − 1</b> 회다. 2박3일이면 2회로, 하루 안 구간(슬롯 수만큼)에 비하면 작다.
     *
     * <p>거리는 여기서 안 잰다 — 좌표만 있으면 응답 시점에 계산되므로 저장할 이유가 없다.
     */
    private void fillDayGaps(List<DaySchedule> days, TransportMode transport) {
        for (int i = 1; i < days.size(); i++) {
            Optional<Slot> from = days.get(i - 1).lastSlot();
            Optional<Slot> to = days.get(i).firstSlot();
            if (from.isEmpty() || to.isEmpty()) {
                continue; // 빈 날은 애초에 목록에 안 들어오지만, 들어와도 지어내지 않는다
            }
            days.get(i).arriveFromPrevDayIn(legMinutes(
                    new Coordinate(from.get().getLat(), from.get().getLng()),
                    new Coordinate(to.get().getLat(), to.get().getLng()),
                    transport));
        }
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
            slots.add(Slot.of(i + 1, e.timeOfDay(), e.kind(), e.poi().contentId(), e.poi().contentTypeId(),
                    e.poi().title(),
                    e.poi().lat(), e.poi().lng(), travel,
                    new SlotDisplay(e.poi().imageUrl(), e.poi().address(), e.poi().catchphrase(), e.poi().tel())));
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
