package com.offway.core.itinerary.service;

import com.offway.core.common.external.CallerContext;
import com.offway.core.common.external.RequestUsage;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CafePreference;
import com.offway.core.itinerary.domain.CourseNeeds;
import com.offway.core.itinerary.domain.CandidatePool;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.DayStart;
import com.offway.core.itinerary.domain.GeoCluster;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.RoutePath;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SightVariety;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.StayPreference;
import com.offway.core.trip.domain.PlaceOrigin;
import com.offway.core.trip.domain.Popularity;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.RouteOptimizer;
import com.offway.core.transport.service.RouteTimeProvider;
import com.offway.core.transport.service.RegionAccessService;
import com.offway.core.transport.service.UnroutableCoordinateService;
import com.offway.core.transport.service.TravelTimeProvider;
import com.offway.core.transport.service.dto.RegionAccess;
import com.offway.core.trip.domain.FoodTaste;
import com.offway.core.trip.domain.RegionVisitMetrics;
import com.offway.core.trip.service.RegionVisitMetricsService;
import com.offway.core.trip.service.RegionPoiService;
import com.offway.core.trip.service.HubAttractionQuery;
import com.offway.core.trip.service.RelatedAttractionQuery;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.service.dto.RegionPois;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.trip.service.TransitHubPhotoProvider;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    /** 상한을 몇 단계까지 푸나 — 한 곳도 못 고를 때만 쓰는 안전판이다. */
    private static final int VARIETY_MAX_RELAX = 3;

    private final RegionPoiService regionPoiService;
    private final TravelTimeProvider travelTimeProvider;
    private final RelatedAttractionQuery relatedAttractionQuery;
    private final HubAttractionQuery hubAttractionQuery;
    private final RouteTimeProvider routeTimeProvider;
    private final RouteOptimizer routeOptimizer;
    private final PolicyService policyService;
    private final CourseWeatherProvider courseWeatherProvider;
    private final OpeningHoursProvider openingHoursProvider;
    private final TransitHubPhotoProvider transitHubPhotoProvider;
    private final FestivalPeriodProvider festivalPeriodProvider;
    private final RegionQuery regionQuery;
    private final RegionAccessService regionAccessService;
    private final UnroutableCoordinateService unroutableCoordinateService;
    private final CourseUsageAlert courseUsageAlert;
    private final RegionVisitMetricsService regionVisitMetricsService;

    public GeneratedCourse generate(GenerateCourse command) {
        // ① POI 수집 (trip)
        return generate(command, regionPoiService.collect(command.regionId(), command.travelDate()));
    }

    /**
     * 코스를 만들고 <b>이 한 건이 태운 외부 호출을 알린다</b>(#421).
     *
     * <p>계측을 진입점에 두는 것이 요점이다. 안쪽 {@code generate(command, pois)} 는 재생성이 씨앗을
     * 바꿔가며 여러 번 부르므로, 거기에 두면 <b>시도마다 알림이 나간다.</b>
     *
     * <p>{@code finally} 로 감싸 <b>실패해도 보낸다</b> — 한도는 이미 깎였고, 오히려 "쓰고도 결과가
     * 없는" 쪽이 더 봐야 하는 숫자다.
     *
     * @param userId 알림에 싣는다. 지금은 인증 뒤라 항상 있지만, 열리면 null 이 온다
     */
    public GeneratedCourse generate(GenerateCourse command, UUID userId) {
        RequestUsage usage = CallerContext.beginUsage();
        boolean succeeded = false;
        try {
            GeneratedCourse generated = generate(command);
            succeeded = true;
            return generated;
        } finally {
            courseUsageAlert.send(userId, command, usage, succeeded, false);
        }
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

        // ⑤ 후보를 고른다 — **근거가 있는 순서부터**(#186·#527).
        //
        // 좌표 군집은 "가까운 것끼리" 라 동선은 짧지만 왜 이 조합인지 답하지 못한다. 함께 가는 순서
        // (연관 관광지)와 많이 찾는 순서(중심관광지)는 실제 방문 데이터라 그 자체가 이유가 된다.
        //
        // 둘 다 없는 지역은 그대로 좌표 군집이다 — degrade 사유는 아래에서 남긴다.
        List<PoiCandidate> sights = selectSights(sightPool, command, needs.sights());
        Coordinate hub = GeoCluster.centroid(coords(sights));
        List<PoiCandidate> foods = selectFoods(foodPool, hub, needs.foods());
        // **숙박은 거리만으로 고르지 않는다**(#510). 야영장이 후보에 들어오면서 그 규칙이 뒤집혔다 —
        // 야영장은 산·계곡·유적 근처라 볼거리 중심에 가까워, 사진 있는 호텔을 사진 없는 야영장이
        // 밀어냈다(실측: 사진 있는 숙소가 141 → 119 로 줄었다).
        List<PoiCandidate> stays = selectStays(stayPool, hub, needs.stays());
        // 카페는 **거리가 아니라 순위로** 고른다(#527). 거리로 고르면 사진 없는 인허가 카페가 이긴다 —
        // 풀의 91~98%가 그쪽이라 수로 밀린다.
        List<PoiCandidate> cafePool = usable(pois.cafes(), command, blocked);
        CafeChoices cafes = selectCafes(cafePool, sights, hub, needs.cafes(), command.regionId());

        // 지역은 날씨·열차 접근 양쪽이 쓴다 — 한 번만 읽는다(#129).
        Region region = regionQuery.byId(command.regionId()).orElse(null);

        // 지역까지 무엇을 타고 가서 어디에 닿는지. 부가 정보라 실패해도 코스는 그대로다.
        // 슬롯 배치보다 앞서야 한다 — 동선의 기준점과 1일차 시작 시간대가 이 결과에서 나온다(#127).
        // 자차도 만든다(#379) — 예전에는 여기서 null 이라 화면의 교통 카드가 통째로 비었다.
        RegionAccess regionAccess = regionAccessFor(command, region);

        // ⑤⑦ interim: 기준점 최근접 정렬(동선) → 하루씩 순서대로 슬라이스하면 가까운 곳끼리 묶인다
        List<PoiCandidate> orderedSights =
                nearestNeighborOrder(sights, command.transport(), regionAnchor(command, regionAccess));

        // ⑥ 슬롯 배치 → ⑨ 조립. 첫날은 도착 시각 이후 남는 시간대만 쓴다.
        // 대중교통이면 첫 칸·끝 칸에 내린 지점(역·터미널·항구)을 세운다(#415).
        List<DaySchedule> days = buildDays(
                command, firstDayStart(command, regionAccess), orderedSights, foods, cafes, stays,
                transitHub(command, regionAccess));
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
                // 지명이 아니라 법정 시군구코드로 찾는다 — 같은 이름이 전국에 여럿이다. DB 만 읽는다.
                .visitMetrics(region == null
                        ? RegionVisitMetrics.none()
                        : regionVisitMetricsService.of(region.getLegalCode()))
                // 받아 둔 것만 읽는다 — 요청 경로에서 외부를 부르지 않는다(#157). 아직 없으면 그 줄이 빈다.
                .hoursByContentId(openingHoursProvider.forCourse(course))
                .hubPhotoUrlByName(transitHubPhotoProvider.photoUrls(transitHubNames(course)))
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
        return selectSights(pool, command, needs.sights()).stream()
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
                command.startDayLeave().departureTime(),
                // 칩을 눌러 수단을 고정했으면 그 수단으로 묻는다(#453). 도착 지점이 바뀌면 아래 동선·
                // 첫날 시각·도착 슬롯이 전부 그 지점 기준으로 다시 계산된다.
                command.transitMode());
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
    /**
     * 방문 순서를 정한다 — <b>최근접으로 잡고 2-opt 로 다듬는다</b>(#531).
     *
     * <p>최근접만 쓰면 되돌아오지 않는 그리디라 마지막에 멀리 튀는 구간이 남는다. 실측(89곳 전수)에서
     * 다듬기를 얹으면 지역 안 이동이 <b>26.5㎞ → 24.0㎞</b> 로 준다(중앙값 3.7% · 42곳이 5% 이상).
     *
     * <p><b>거리 행렬을 한 번만 만든다.</b> 예전에는 최근접을 도는 동안 매번 provider 를 불렀는데, 그
     * 호출 수가 이미 O(n²) 다. 한 번 만들어 두면 다듬기는 배열 읽기라 <b>호출이 더 늘지 않는다.</b>
     *
     * <p>행렬 비용은 {@code travelMinutes} 라 외부를 안 탄다 — 지역 안 구간은 40㎞ 미만이라 직선거리
     * 근사로 떨어진다. TMAP 은 하루 이웃 구간에서만 부른다.
     */
    private List<PoiCandidate> nearestNeighborOrder(
            List<PoiCandidate> pois, TransportMode transport, Coordinate anchor) {
        if (pois.size() < 2) {
            return List.copyOf(pois);
        }
        int[][] cost = costMatrix(pois, transport, anchor);
        List<Integer> greedy = greedyOrder(cost, pois.size());
        return RoutePath.improve(cost, greedy).stream().map(stop -> pois.get(stop - 1)).toList();
    }

    /**
     * 0 번이 출발점, 1..n 이 들를 곳인 대칭 비용 행렬.
     *
     * <p><b>대칭으로 채운다.</b> 한 방향만 재고 반대편에 같은 값을 넣는다 — 계산이 절반이고, 2-opt 가
     * 기대는 대칭 가정을 행렬이 스스로 지킨다.
     */
    private int[][] costMatrix(List<PoiCandidate> pois, TransportMode transport, Coordinate anchor) {
        int size = pois.size() + 1;
        Coordinate[] points = new Coordinate[size];
        points[0] = anchor;
        for (int i = 0; i < pois.size(); i++) {
            points[i + 1] = coord(pois.get(i));
        }
        int[][] cost = new int[size][size];
        for (int from = 0; from < size; from++) {
            for (int to = from + 1; to < size; to++) {
                int minutes = travelTimeProvider.reachMinutes(points[from], points[to], transport);
                cost[from][to] = minutes;
                cost[to][from] = minutes;
            }
        }
        return cost;
    }

    /** 출발점에서 가장 가까운 곳부터 훑는다 — 다듬기의 출발선이다. */
    private static List<Integer> greedyOrder(int[][] cost, int stops) {
        List<Integer> remaining = new ArrayList<>();
        for (int stop = 1; stop <= stops; stop++) {
            remaining.add(stop);
        }
        List<Integer> ordered = new ArrayList<>(stops);
        int current = 0;
        while (!remaining.isEmpty()) {
            int from = current;
            int next = remaining.stream()
                    .min(Comparator.comparingInt(stop -> cost[from][stop]))
                    .orElseThrow();
            ordered.add(next);
            remaining.remove(Integer.valueOf(next));
            current = next;
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
            List<PoiCandidate> sights, List<PoiCandidate> foods, CafeChoices cafes,
            List<PoiCandidate> stays, TransitHub hub) {
        int perDaySights = command.density().sightsPerDay();
        List<DaySchedule> days = new ArrayList<>();
        // **상한은 날짜 배정에서 건다**(#522). 고를 때 걸면 그 뒤 동선 정렬이 순서를 바꿔 하루 단위가
        // 어긋난다 — 실제로 그렇게 만들었더니 해수욕장이 통째로 이튿날로 밀렸다.
        List<PoiCandidate> remaining = new ArrayList<>(sights);
        int fi = 0;
        List<PoiCandidate> remainingByRank = new ArrayList<>(cafes.byRank());
        List<PoiCandidate> remainingRest = new ArrayList<>(cafes.rest());
        int sti = 0;
        for (int day = 1; day <= command.travelDays(); day++) {
            DayStart start = day == 1 ? firstDayStart : DayStart.fullDay();
            List<PoiCandidate> daySights = takeVaried(remaining, start.sightCapacity(perDaySights));
            if (command.transport() == TransportMode.CAR) {
                // 하루 볼거리 순서를 실도로 기준 최적화(자차). TMAP 은 실도로라 우리 추정보다 낫다.
                daySights = reorder(daySights, routeOptimizer.optimalOrder(coords(daySights)));
            }
            // 대중교통은 여기서 따로 안 다듬는다(#531). 전체 순서를 이미 2-opt 로 다듬어 뒀고, 하루만
            // 떼어 다시 맞추면 **다음 날로 넘어가는 구간을 못 본다** — 그날은 짧아져도 이튿날 첫 이동이
            // 늘어 전체가 나빠질 수 있다. 실측에서도 하루 단위 추가 이득은 중앙값 0.0㎞ 였다.
            List<PoiCandidate> dayFoods = slice(foods, fi, start.mealCapacity());
            fi += dayFoods.size();
            // 카페는 점심 뒤 한 칸이라, 점심이 없는 날(늦게 시작한 첫날)에는 넣지 않는다.
            // **그날 볼거리에 가장 가까운 것**을 준다 — 순서대로 꽂으면 그날 동선과 무관한 곳이 걸린다.
            PoiCandidate cafe = dayFoods.isEmpty()
                    ? null : takeNearestCafe(remainingByRank, remainingRest, daySights);
            boolean lastDay = day == command.travelDays();
            PoiCandidate stay = (!lastDay && sti < stays.size()) ? stays.get(sti++) : null;

            List<Slot> slots = arrangeDay(daySights, dayFoods, cafe, stay, command.transport(), start);
            if (!slots.isEmpty()) {
                // 표시 번호는 1부터 연속(빈 날은 건너뛴다), 날짜 계산용 오프셋은 달력을 그대로 따른다.
                // 둘을 겸하면 첫날이 빌 때 날짜와 날씨가 하루 앞당겨진다(#159).
                days.add(DaySchedule.of(days.size() + 1, day - 1, slots));
            }
        }
        if (days.isEmpty()) {
            throw ItineraryException.courseNotBuildable();
        }
        addTransitHubSlots(days, hub, command.transport());
        fillDayGaps(days, command.transport());
        return days;
    }

    /**
     * 대중교통 코스의 <b>첫 칸과 끝 칸</b>을 내린 지점으로 채운다(#415).
     *
     * <p>기차에서 내려 역에서 시작하고 마지막에 다시 역으로 간다. 그 두 칸이 없으면 "역에서 첫 장소까지
     * 어떻게 가지" 와 "언제 역으로 나서지" 를 사용자가 코스 밖에서 따로 계산한다.
     *
     * <p><b>남아 있는 날에 붙인다.</b> 자정을 넘겨 닿으면 1일차가 통째로 빠지는데(#159), 그때 도착은
     * 실제로 그 다음 날 아침에 일어난다. 요청한 1일차가 아니라 <b>목록의 첫 날</b>에 붙여야 맞는 이유다.
     *
     * <p><b>시간대는 이웃 칸에서 가져온다.</b> 도착 칸은 뒤따르는 첫 일정과, 출발 칸은 앞선 마지막 일정과
     * 같은 시간대다. 여기서 시각을 새로 정하면 {@link DayStart} 가 이미 내린 판단과 갈린다 — 실제 출발
     * 시각(몇 시 차)은 교통 카드의 시간표가 답한다(#414).
     *
     * <p>여기서 {@link DaySchedule} 을 새로 만드는 것은 안전하다 — 아직 영속 전이라 {@code orphanRemoval}
     * 이 관여하지 않는다(저장 뒤에 같은 짓을 하면 슬롯이 지워진다. {@code Course.renumber} 주석 참고).
     */
    private void addTransitHubSlots(List<DaySchedule> days, TransitHub hub, TransportMode transport) {
        if (hub == null) {
            return; // 자차이거나, 내린 지점을 모른다 — 지어내지 않는다
        }
        DaySchedule first = days.getFirst();
        List<Slot> opened = withArrival(first.getSlots(), hub, transport);
        days.set(0, DaySchedule.of(first.getDayNumber(), first.getDayOffset(), opened));

        int lastIndex = days.size() - 1;
        // 하루짜리 코스면 방금 도착 칸을 붙인 그 날이다 — 원본이 아니라 갱신된 쪽을 다시 읽는다.
        DaySchedule last = days.get(lastIndex);
        List<Slot> closed = withDeparture(last.getSlots(), hub, transport);
        days.set(lastIndex, DaySchedule.of(last.getDayNumber(), last.getDayOffset(), closed));
    }

    /** 하루의 맨 앞에 도착 칸을 세우고, 뒤 슬롯의 순서·이동시간을 다시 매긴다. */
    private List<Slot> withArrival(List<Slot> slots, TransitHub hub, TransportMode transport) {
        Slot head = slots.getFirst();
        List<Slot> opened = new ArrayList<>();
        opened.add(Slot.transitHub(1, head.getTimeOfDay(), SlotKind.ARRIVAL, hub.name(),
                hub.point().lat(), hub.point().lng(), 0));
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            // 첫 장소의 이동시간이 0 에서 "역에서 여기까지" 로 바뀐다 — 이 이슈가 채우려던 값이다.
            int travel = i == 0
                    ? legMinutes(hub.point(), new Coordinate(slot.getLat(), slot.getLng()), transport)
                    : slot.getTravelMinutesFromPrev();
            opened.add(renumbered(slot, i + 2, travel));
        }
        return opened;
    }

    /** 하루의 맨 뒤에 출발 칸을 잇는다 — 마지막 장소에서 지점까지의 이동시간을 함께 잰다. */
    private List<Slot> withDeparture(List<Slot> slots, TransitHub hub, TransportMode transport) {
        Slot tail = slots.getLast();
        List<Slot> closed = new ArrayList<>(slots);
        closed.add(Slot.transitHub(slots.size() + 1, tail.getTimeOfDay(), SlotKind.DEPARTURE, hub.name(),
                hub.point().lat(), hub.point().lng(),
                legMinutes(new Coordinate(tail.getLat(), tail.getLng()), hub.point(), transport)));
        return closed;
    }

    /** 순서와 이동시간만 바꾼 같은 슬롯 — 나머지 값은 그대로 옮긴다. */
    private static Slot renumbered(Slot slot, int orderInDay, int travelMinutesFromPrev) {
        return Slot.of(orderInDay, slot.getTimeOfDay(), slot.getKind(), slot.getPoiContentId(),
                slot.getPoiContentTypeId(), slot.getTitle(), slot.getLat(), slot.getLng(), travelMinutesFromPrev,
                new SlotDisplay(slot.getImageUrl(), slot.getAddress(), slot.getCatchphrase(), slot.getTel()));
    }

    /**
     * 대중교통 코스가 내리는 지점 — 이름과 좌표가 <b>둘 다</b> 있을 때만.
     *
     * <p>자차는 내릴 역이 없다. 대중교통이라도 오지라 역이 안 잡히거나 접근 조회가 실패하면 지점을
     * 모르는데, 그때는 지금처럼 관광지로 시작한다 — 모르는 것을 지어내지 않는다.
     */
    private static TransitHub transitHub(GenerateCourse command, RegionAccess regionAccess) {
        if (command.transport() != TransportMode.TRANSIT || regionAccess == null) {
            return null;
        }
        String name = regionAccess.toName();
        return regionAccess.arrivalPoint()
                .filter(point -> name != null && !name.isBlank())
                .map(point -> new TransitHub(name, point))
                .orElse(null);
    }

    /** 대중교통 코스가 내리고 다시 타는 지점 — 역·터미널·항구를 한 모양으로 접는다. */
    private record TransitHub(String name, Coordinate point) {
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
    /**
     * 하루치 볼거리를 <b>같은 종류가 몰리지 않게</b> 집는다(#522).
     *
     * <p>앞에서 정한 순서(연관 관광지·동선)를 훑되 그날 상한에 걸린 것은 건너뛴다. 건너뛴 것은 목록에
     * 남아 <b>이튿날 몫</b>이 된다 — 태안처럼 해수욕장이 몰린 지역에서 하루에 하나씩 나뉜다.
     *
     * <p><b>고르는 단계는 안 건드린다.</b> 후보를 더 넓게 집으면 재생성 씨앗이 무력해진다 — 요청 수가
     * 풀 크기를 넘으면 {@code selectCompact} 가 씨앗과 무관하게 전부 돌려주기 때문이다. 실제로 그렇게
     * 만들었더니 <b>재생성해도 같은 코스</b>가 나왔다. 여기서는 이미 고른 것을 날짜에 나눌 뿐이다.
     *
     * <p><b>못 채우면 그냥 적게 넣는다.</b> 상한을 풀어 채우면 남은 것이 한 종류뿐인 날이 그것으로
     * 도배된다 — 실제로 마지막 날이 해수욕장 넷이 됐다. 여섯 칸을 같은 것으로 채우는 것보다 네 칸이라도
     * 다른 것을 넣는 편이 낫다. 한 곳도 못 고를 때만 상한을 버린다(빈 날은 코스에서 통째로 빠진다).
     */
    private static List<PoiCandidate> takeVaried(List<PoiCandidate> remaining, int capacity) {
        for (int step = 0; step <= VARIETY_MAX_RELAX; step++) {
            List<PoiCandidate> picked = pickWithin(remaining, capacity, step);
            if (!picked.isEmpty()) {
                remaining.removeAll(picked);
                return picked;
            }
        }
        List<PoiCandidate> fallback = List.copyOf(remaining.subList(0, Math.min(capacity, remaining.size())));
        remaining.removeAll(fallback);
        return fallback;
    }

    private static List<PoiCandidate> pickWithin(List<PoiCandidate> remaining, int capacity, int step) {
        SightVariety day = step == 0 ? SightVariety.strict() : SightVariety.relaxedBy(step);
        List<PoiCandidate> picked = new ArrayList<>();
        for (PoiCandidate candidate : remaining) {
            if (picked.size() >= capacity) {
                break;
            }
            if (day.accepts(candidate.sightKind())) {
                day.add(candidate.sightKind());
                picked.add(candidate);
            }
        }
        return picked;
    }

    private List<Slot> arrangeDay(List<PoiCandidate> sights, List<PoiCandidate> foods, PoiCandidate cafe,
            PoiCandidate stay, TransportMode transport, DayStart start) {
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
            // **밥 다음은 카페다**(#522). 점심 바로 뒤에 끼운다 — 오후 관광 앞이라 동선이 이어진다.
            if (cafe != null) {
                entries.add(morning + 1, new Entry(SlotKind.CAFE, TimeOfDay.AFTERNOON, cafe));
            }
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
    /**
     * 함께 가는 순서로 후보를 고른다(#186) — <b>없으면 빈 값이고 호출자가 좌표 군집으로 되돌아간다.</b>
     *
     * <h2>왜 폴백이 필요한가</h2>
     *
     * <p>연관 데이터는 지역마다 있고 없다. 원본이 그 지자체를 아직 안 냈거나, 냈어도 이름을 인허가와
     * 못 이어 좌표가 빠졌을 수 있다. 그때 코스가 안 나가면 안 된다.
     *
     * <h2>모자라면 섞는다</h2>
     *
     * <p>연관 상위가 필요 수에 못 미치면 나머지는 좌표 군집이 채운다. 연관으로 고른 것을 앞에 두므로
     * 앞쪽 슬롯이 더 나은 근거를 갖는다.
     *
     * <p><b>degrade 는 사유를 남긴다.</b> 조용히 좌표 군집으로 떨어지면 연관 데이터가 안 쌓이고
     * 있어도 아무도 모른다.
     */
    /**
     * 볼거리를 고르는 <b>유일한 경로</b>. 연관 순서가 있으면 그것, 없으면 좌표 군집이다.
     *
     * <p><b>{@link #generate} 와 {@link #selectedSightIds} 가 반드시 이것을 함께 쓴다.</b> 재생성은
     * 씨앗마다 이 결과를 비교해 "충분히 다른가" 를 판정하는데, 판정과 실제 코스가 다른 방식으로 고르면
     * 판정이 화면에 없는 장소를 세게 된다.
     */
    /**
     * 볼거리를 고른다 — <b>근거가 있는 순서부터</b>.
     *
     * <ol>
     *   <li><b>함께 가는 순서</b>(연관 관광지) — 실제 방문 데이터라 "왜 이 조합인지" 를 답한다
     *   <li><b>많이 찾는 순서</b>(중심관광지 인기순, #527) — 위가 안 걸릴 때
     *   <li><b>좌표 군집</b> — 둘 다 없으면 동선만 본다
     * </ol>
     *
     * <p><b>왜 인기순을 더했나.</b> 연관 순서는 인허가 볼거리({@code LIC-})에만 걸리는데, 인허가 볼거리는
     * {@code MIN_SIGHTS}(18)에 못 미칠 때만 보충된다. 실측에서 TourAPI 볼거리가 양양 71·보령 68·태안
     * 63·완도 35 라 <b>보충이 안 돌고</b>, 그래서 연관 경로가 후보 풀에 한 건도 안 걸린다 — 볼거리에는
     * 사실상 아무 품질 신호도 없이 기하학만 돌고 있었다.
     *
     * <p>하루 같은 분류 상한(#522)은 그대로다. 인기순은 <b>그 상한 안에서 무엇을 고를지</b>를 정한다.
     */
    private List<PoiCandidate> selectSights(List<PoiCandidate> pool, GenerateCourse command, int needed) {
        return byRelation(pool, command, needed)
                .or(() -> byPopularity(pool, command, needed))
                .orElseGet(() -> reorder(
                        pool, GeoCluster.selectCompact(coords(pool), needed, seedIndexOf(command))));
    }

    /**
     * 많이 찾는 순서로 고른다(#527).
     *
     * <p>맞추는 규칙과 그 한계는 {@link Popularity} 가 소유한다. 실측에서 볼거리 풀의 17~26%가 순위를
     * 얻는데, 코스 하나가 쓰는 볼거리가 6~12곳이라 대개 여기서 채워지고 모자란 만큼만 좌표 군집이 받는다.
     */
    private Optional<List<PoiCandidate>> byPopularity(
            List<PoiCandidate> pool, GenerateCourse command, int needed) {
        long regionId = command.regionId();
        Popularity popularity = hubAttractionQuery.sightPopularity(regionId);
        if (popularity.isEmpty()) {
            return Optional.empty();
        }
        record Ranked(String contentId, int rank) { }
        List<String> ordered = pool.stream()
                .map(candidate -> popularity.rankOf(candidate.title(), candidate.lat(), candidate.lng())
                        .stream()
                        .mapToObj(rank -> new Ranked(candidate.contentId(), rank))
                        .findFirst())
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(Ranked::rank))
                .map(Ranked::contentId)
                .toList();
        if (ordered.isEmpty()) {
            log.debug("중심관광지가 후보 풀에 안 걸려 좌표 군집으로 짭니다 regionId={}", regionId);
            return Optional.empty();
        }
        log.debug("인기순으로 볼거리를 고릅니다 regionId={} 순위가 붙은 후보={}/{}",
                regionId, ordered.size(), pool.size());
        return pickOrdered(pool, ordered, command, needed);
    }

    private Optional<List<PoiCandidate>> byRelation(List<PoiCandidate> pool, GenerateCourse command, int needed) {
        long regionId = command.regionId();
        List<String> ordered = relatedAttractionQuery.sightPlaceIds(regionId);
        if (ordered.isEmpty()) {
            log.debug("연관 관광지가 없어 다음 근거로 넘어갑니다 regionId={}", regionId);
            return Optional.empty();
        }
        return pickOrdered(pool, ordered, command, needed);
    }

    /**
     * 순위가 매겨진 식별자 순서대로 후보를 집는다 — 연관 순서와 인기순이 함께 쓴다.
     *
     * @return 하나도 못 걸리면 비어 있음. 호출자가 다음 근거로 넘어간다
     */
    private Optional<List<PoiCandidate>> pickOrdered(
            List<PoiCandidate> pool, List<String> ordered, GenerateCourse command, int needed) {
        long regionId = command.regionId();
        Map<String, PoiCandidate> byId = new LinkedHashMap<>();
        pool.forEach(candidate -> byId.putIfAbsent(candidate.contentId(), candidate));

        // **씨앗만큼 순위를 밀어 시작점을 옮긴다.** 안 옮기면 "다시 추천" 이 몇 번을 눌러도 같은 코스를
        // 낸다 — 연관 순서는 씨앗과 무관하게 고정이라 앞쪽 몇 곳이 매번 그대로 뽑힌다.
        //
        // 섞지 않고 미는 이유는 순위에 뜻이 있어서다. 1위가 2위보다 함께 가는 정도가 크므로, 무작위로
        // 흩으면 근거가 사라진다. 미는 것은 "상위권 안에서 창을 옮기는" 것이라 근거가 남는다.
        int start = Math.floorMod(seedIndexOf(command), ordered.size());

        List<PoiCandidate> picked = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            PoiCandidate candidate = byId.remove(ordered.get((start + i) % ordered.size()));
            if (candidate != null) {
                picked.add(candidate);
            }
            if (picked.size() >= needed) {
                break;
            }
        }
        if (picked.isEmpty()) {
            // 순위는 있는데 이번 후보 풀에 하나도 안 걸렸다 — 연관이면 보충이 안 돌아 인허가가 안 실린
            // 경우이고, 인기순이면 이름·좌표가 하나도 안 맞은 경우다. 호출자가 다음 근거로 넘어간다.
            log.debug("순위가 후보 풀에 없어 다음 근거로 넘어갑니다 regionId={} 순위={}건",
                    regionId, ordered.size());
            return Optional.empty();
        }
        if (picked.size() < needed) {
            // 모자란 만큼 좌표 군집으로 채운다. 남은 것 중에서 고르므로 중복이 없다.
            List<PoiCandidate> rest = new ArrayList<>(byId.values());
            int more = Math.min(needed - picked.size(), rest.size());
            int fromRank = picked.size();
            picked.addAll(reorder(rest, GeoCluster.selectCompact(coords(rest), more, seedIndexOf(command))));
            log.info("순위로 {}곳, 좌표 군집으로 {}곳을 채웠습니다 regionId={}", fromRank, more, regionId);
        }
        return Optional.of(List.copyOf(picked));
    }

    /**
     * 잘 곳을 고른다 — <b>등급을 먼저 보고, 같은 등급 안에서 가까운 순</b>(#510).
     *
     * <p>야영장을 후보에 더하기 전에는 거리만 봐도 됐다. 후보가 관광 API 숙소뿐이라 등급이 하나였기
     * 때문이다. 그 상태로 야영장을 넣자 <b>사진 있는 숙소가 141 → 119 로 줄었다</b> — 야영장이 볼거리
     * 중심에 가까워 호텔을 밀어냈다.
     *
     * <p>등급 순서와 실측 근거는 {@link StayPreference} 가 소유한다. 여기서는 그 순서대로 채우고,
     * 다 찼으면 다음 등급을 <b>읽지도 않는다</b> — 대부분의 지역이 첫 등급에서 끝난다.
     */
    private static List<PoiCandidate> selectStays(List<PoiCandidate> pool, Coordinate hub, int needed) {
        List<PoiCandidate> picked = new ArrayList<>();
        for (StayPreference tier : StayPreference.values()) {
            if (picked.size() >= needed) {
                break;
            }
            List<PoiCandidate> inTier = pool.stream()
                    .filter(candidate -> tier.covers(isCamping(candidate), hasPhoto(candidate)))
                    .toList();
            if (inTier.isEmpty()) {
                continue;
            }
            picked.addAll(reorder(inTier,
                    GeoCluster.nearest(coords(inTier), hub, needed - picked.size())));
        }
        return List.copyOf(picked);
    }

    /**
     * 야영장인가 — <b>식별자의 출처가 답한다</b>.
     *
     * <p>{@code PlaceOrigin} 이 접두어 파싱을 이미 소유하므로 여기서 {@code CMP-} 를 다시 적지 않는다.
     * 한쪽만 바뀌면 조용히 틀린 등급으로 떨어진다.
     */
    private static boolean isCamping(PoiCandidate candidate) {
        return PlaceOrigin.of(candidate.contentId()) == PlaceOrigin.CAMPING;
    }

    private static boolean hasPhoto(PoiCandidate candidate) {
        return candidate.imageUrl() != null && !candidate.imageUrl().isBlank();
    }

    /**
     * 카페를 고른다 — <b>순위를 먼저 보고, 없으면 사진, 그것도 없으면 거리</b>(#527).
     *
     * <p>등급의 근거는 {@link CafePreference} 가 소유한다. 여기서는 그 순서대로 채우고, 다 찼으면
     * 다음 등급을 <b>읽지도 않는다</b> — 89곳 중 56곳이 첫 등급만으로 2박3일을 채운다.
     *
     * <p>순위가 있는 등급은 순위대로, 나머지는 가까운 순이다. 근거가 없는 것끼리는 견줄 자가 거리뿐이다.
     */
    private CafeChoices selectCafes(
            List<PoiCandidate> pool, List<PoiCandidate> sights, Coordinate hub, int needed, long regionId) {
        if (pool.isEmpty() || needed <= 0) {
            return CafeChoices.none();
        }
        List<PoiCandidate> byRank = new ArrayList<>();
        Map<String, Integer> ranks = rankByContentId(relatedAttractionQuery.foodPlaceIds(regionId));
        List<PoiCandidate> picked = new ArrayList<>();
        for (CafePreference tier : CafePreference.values()) {
            if (picked.size() >= needed) {
                break;
            }
            List<PoiCandidate> inTier = pool.stream()
                    .filter(candidate -> tier.covers(
                            ranks.containsKey(candidate.contentId()), hasPhoto(candidate)))
                    // 순위를 따르되 **권역 밖은 건너뛴다**. 근거와 30㎞ 의 실측은 CafePreference 가 소유한다.
                    .filter(candidate -> !tier.ordersByRank() || withinDetour(candidate, sights))
                    .toList();
            if (inTier.isEmpty()) {
                continue;
            }
            int room = needed - picked.size();
            if (tier.ordersByRank()) {
                List<PoiCandidate> ranked = inTier.stream()
                        .sorted(Comparator.comparingInt(candidate -> ranks.get(candidate.contentId())))
                        .limit(room)
                        .toList();
                picked.addAll(ranked);
                byRank.addAll(ranked);
            } else {
                picked.addAll(reorder(inTier, GeoCluster.nearest(coords(inTier), hub, room)));
            }
        }
        List<PoiCandidate> rest = picked.stream().filter(candidate -> !byRank.contains(candidate)).toList();
        return new CafeChoices(List.copyOf(byRank), rest);
    }

    /**
     * 고른 카페를 <b>등급 블록으로 갈라</b> 들고 있는다(#527).
     *
     * <p>날짜 배정은 거리로 하는데, 그 거리가 <b>등급을 넘지는 못하게</b> 하려는 것이다. 순위로 고른
     * 카페가 사진만 있는 카페에게 "더 가깝다" 는 이유로 자리를 뺏기면 순위를 먼저 본 뜻이 사라진다.
     *
     * <p>슬롯이 고른 수보다 적을 때 그 차이가 드러난다 — 늦게 도착한 첫날처럼 점심이 없는 날이 있으면
     * 카페 자리가 하루치 줄어든다. 그때 밀려나는 것은 <b>순위가 낮은 쪽</b>이어야 한다.
     */
    private record CafeChoices(List<PoiCandidate> byRank, List<PoiCandidate> rest) {

        static CafeChoices none() {
            return new CafeChoices(List.of(), List.of());
        }

        boolean isEmpty() {
            return byRank.isEmpty() && rest.isEmpty();
        }
    }

    /**
     * 이 카페가 코스에서 들를 만한 자리인가 — <b>가장 가까운 볼거리</b>를 기준으로 잰다.
     *
     * <p>볼거리 중심(centroid)으로 재면 안 된다. 지역이 넓게 퍼진 곳에서는 중심이 아무 볼거리도 없는
     * 한가운데가 되어, <b>2일차 코앞에 있는 카페까지 "멀다" 로 잘린다.</b> 우리가 막으려는 것은
     * "코스 어디에서도 못 들르는 곳" 이지 "중심에서 먼 곳" 이 아니다.
     */
    private static boolean withinDetour(PoiCandidate candidate, List<PoiCandidate> sights) {
        Coordinate at = new Coordinate(candidate.lat(), candidate.lng());
        return sights.stream().anyMatch(sight ->
                new Coordinate(sight.lat(), sight.lng()).haversineKmTo(at) <= CafePreference.DETOUR_LIMIT_KM);
    }

    /**
     * 그날 볼거리에 <b>가장 가까운</b> 카페를 꺼내 쓴다(#527) — "여기 들른 김에 저기도".
     *
     * <p>무엇을 고를지는 순위가 정하고, <b>어느 날에 놓을지는 거리가 정한다.</b> 예전에는 배열 순서대로
     * 꽂아서, 2일차가 북쪽인데 남쪽 카페를 받는 일이 생겼다 — 고른 카페가 아무리 좋아도 그날 동선과
     * 무관하면 못 간다.
     *
     * <p>꺼낸 것은 목록에서 지운다. 남은 것끼리 다음 날을 겨루므로 같은 카페가 두 번 안 들어간다.
     *
     * <p>그날 볼거리가 없으면 견줄 기준이 없어 맨 앞(가장 높은 순위)을 준다.
     */
    private static PoiCandidate takeNearestCafe(
            List<PoiCandidate> byRank, List<PoiCandidate> rest, List<PoiCandidate> daySights) {
        // **순위 블록을 먼저 비운다.** 거리는 같은 블록 안에서만 겨룬다 — 등급을 넘어 겨루면
        // 순위로 고른 카페가 "더 가깝다" 는 이유로 밀려난다.
        return nearestFrom(byRank.isEmpty() ? rest : byRank, daySights);
    }

    private static PoiCandidate nearestFrom(List<PoiCandidate> remaining, List<PoiCandidate> daySights) {
        if (remaining.isEmpty()) {
            return null;
        }
        if (daySights.isEmpty()) {
            return remaining.removeFirst();
        }
        Coordinate center = GeoCluster.centroid(coords(daySights));
        PoiCandidate nearest = remaining.stream()
                .min(Comparator.comparingDouble(
                        candidate -> center.haversineKmTo(new Coordinate(candidate.lat(), candidate.lng()))))
                .orElseThrow();
        remaining.remove(nearest);
        return nearest;
    }

    /**
     * 순위가 매겨진 식별자 목록을 "식별자 → 순위" 로 뒤집는다.
     *
     * <p>같은 식별자가 두 번 오면 <b>앞선(더 높은) 순위</b>를 남긴다 — 목록이 이미 순위순이라 뒤엣것은
     * 더 낮은 순위다.
     */
    private static Map<String, Integer> rankByContentId(List<String> ordered) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            ranks.putIfAbsent(ordered.get(i), i);
        }
        return ranks;
    }

    /**
     * 끼니 후보 — 가까운 순으로 고르되 <b>같은 음식을 연달아 넣지 않는다</b>.
     *
     * <p><b>왜 필요했나.</b> 지역 특산이 곧 음식점 풀이라 어디서나 같은 것이 반복된다 — 실측(2026-09-08)
     * 에서 태안 49건 중 꽃게가 9건, 평창 100건 중 메밀 11건, 횡성 23건 중 한우 4건이었다. 거리만 보고
     * 고르면 점심도 한우, 저녁도 한우가 된다(실제 코스에서 그렇게 나왔다).
     *
     * <p><b>거리를 버리지 않는다.</b> 가까운 것부터 훑되, 직전에 고른 것과 같은 음식이면 <b>미뤄 뒀다가</b>
     * 다른 음식을 못 찾았을 때 쓴다. 순서를 통째로 뒤집는 것이 아니라 한 칸씩 양보하는 것이라 동선이
     * 크게 흔들리지 않는다.
     *
     * <p><b>모르면 다른 음식으로 본다</b>({@link FoodTaste}). 상호로 읽히는 것이 33% 뿐이라, 확신 없이
     * 후보를 미루면 사진 있는 좋은 카드가 근거 없이 밀려난다 — 겹침을 덜 막는 쪽이 잘못 막는 쪽보다 낫다.
     *
     * <p><b>후보가 모자라면 겹쳐도 넣는다.</b> 군위·장수는 음식점이 두 곳뿐이라 미룰 곳이 없다.
     * 빈 슬롯이 중복보다 나쁘다.
     */
    private static List<PoiCandidate> selectFoods(List<PoiCandidate> pool, Coordinate hub, int needed) {
        if (pool.isEmpty() || needed <= 0) {
            return List.of();
        }
        // 풀 전체를 거리순으로 훑는다 — 필요한 만큼만 뽑으면 양보할 후보가 애초에 없다.
        List<PoiCandidate> byDistance = reorder(pool, GeoCluster.nearest(coords(pool), hub, pool.size()));
        List<PoiCandidate> picked = new ArrayList<>();
        List<PoiCandidate> deferred = new ArrayList<>();
        for (PoiCandidate candidate : byDistance) {
            if (picked.size() >= needed) {
                break;
            }
            if (repeatsLast(picked, candidate)) {
                deferred.add(candidate);
                continue;
            }
            picked.add(candidate);
        }
        // 다른 음식으로 못 채웠으면 미뤄 둔 것을 도로 쓴다.
        for (PoiCandidate candidate : deferred) {
            if (picked.size() >= needed) {
                break;
            }
            picked.add(candidate);
        }
        return List.copyOf(picked);
    }

    /** 직전에 고른 것과 같은 음식인가 — 하루 안이든 이튿날이든 <b>연달아</b> 나오는 것을 본다. */
    private static boolean repeatsLast(List<PoiCandidate> picked, PoiCandidate candidate) {
        if (picked.isEmpty()) {
            return false;
        }
        PoiCandidate last = picked.getLast();
        return FoodTaste.same(last.title(), last.foodCategory(), candidate.title(), candidate.foodCategory());
    }

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

    /**
     * 이 코스의 교통 거점 이름 — 도착·출발 칸이다(#450).
     *
     * <p>장소 칸은 생성 때 받은 사진을 슬롯이 들고 있고, 교통 거점만 이름으로 따로 얻는다. 둘이 같은
     * 지점이라 보통 하나다.
     */
    private static Set<String> transitHubNames(Course course) {
        return course.getDays().stream()
                .flatMap(day -> day.getSlots().stream())
                .filter(slot -> !slot.getKind().hasPlace())
                .map(Slot::getTitle)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
