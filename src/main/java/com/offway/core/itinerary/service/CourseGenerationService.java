package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseNeeds;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.RouteOptimizer;
import com.offway.core.transport.service.RouteTimeProvider;
import com.offway.core.transport.service.TravelTimeProvider;
import com.offway.core.trip.service.RegionPoiService;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.service.dto.RegionPois;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public GeneratedCourse generate(GenerateCourse command) {
        // ① POI 수집 (trip)
        RegionPois pois = regionPoiService.collect(command.regionId());

        // ④ 필요 개수 (밀도×일수) + ② interim 랭킹(TourAPI 순서) 상위 선택
        CourseNeeds needs = CourseNeeds.of(command.density(), command.travelDays());
        List<PoiCandidate> sights = take(pois.sights(), needs.sights());
        List<PoiCandidate> foods = take(pois.foods(), needs.foods());
        List<PoiCandidate> stays = take(pois.stays(), needs.stays());
        if (sights.isEmpty()) {
            throw ItineraryException.courseNotBuildable(); // 볼거리가 없으면 코스가 아니다(식사만 있는 코스 방지)
        }

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

        log.info("코스 생성 regionId={} days={} slots={} benefits={}",
                command.regionId(), course.getTravelDays(), course.totalSlots(), benefits.size());
        return new GeneratedCourse(course, benefits);
    }

    private static List<PoiCandidate> take(List<PoiCandidate> pool, int count) {
        return pool.stream().limit(Math.max(0, count)).toList();
    }

    /** 출발지에서 가장 가까운 곳부터 이어붙이는 그리디 정렬(TSP 근사). TMAP(#25) 오면 경유지 최적화로 교체. */
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
                    e.poi().lat(), e.poi().lng(), travel));
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
