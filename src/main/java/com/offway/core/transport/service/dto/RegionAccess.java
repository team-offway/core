package com.offway.core.transport.service.dto;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.RegionArrival;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;

/**
 * 지역까지의 대중교통 접근 결과 — transport 가 itinerary(코스)에 주는 값. 상태를 구분해 UI 가 정확히 안내하게 한다.
 *
 * <p><b>도착 지점과 도착 시각을 함께 준다.</b> 코스는 집이 아니라 <b>내린 곳에서</b> 시작하고, 오후에 닿았으면 그날 오전
 * 일정은 지킬 수 없다. 이 둘이 없으면 itinerary 는 출발지 좌표와 1일차 오전이라는 <b>틀린 전제</b>로 코스를 짠다(#127).
 *
 * <p>둘의 <b>가용 조건이 다르다</b> — 도착 지점은 역·터미널·항구가 해석되기만 하면 알 수 있고(그날 운행이 없어도, 조회가
 * 실패해도), 도착 시각은 실제 운행 편을 찾았을 때만 안다. 그래서 별도 필드로 두고 각각 {@link Optional} 로 답한다.
 *
 * <p><b>열차 전용이었던 것을 수단 전체로 넓혔다</b>(#97). 예전 이름은 {@code TrainAccess} 였는데, 89곳 중 열차로 닿는
 * 곳이 일부뿐이라 나머지는 도착 지점조차 몰라 출발지 좌표로 되돌아갔다. 버스 터미널 789곳·항구 500곳이 이미 시드돼
 * 있는데도 코스가 쓰지 않던 상태다.
 *
 * @param mode 이 결과를 만든 수단
 * @param status 접근 상태
 * @param fromName 출발 지점명(역·터미널·항구, 없으면 null)
 * @param toName 도착 지점명(없으면 null)
 * @param toPoint 도착 지점 좌표(해석됐으면 non-null) — 지역 안 동선의 기준점
 * @param fastest 가장 빠른 운행 편({@link Status#AVAILABLE} 일 때만, 아니면 null)
 * @param durationMinutes 소요시간(분, 모르면 null). 버스·여객선은 저장해 둔 구간 측정값에서, 자차는
 *     출발지→지역 이동시간에서 온다. 시간표를 못 묻는 수단이라 <b>시각 대신 이것으로</b> 도착 시각을
 *     만든다(#107 · #379)
 * @param distanceKm 출발지에서 도착 지점까지의 직선거리(㎞, 모르면 null). 화면이 "약 2시간 29분 · 200km"
 *     로 소요시간 옆에 붙인다(#379). 실제 주행거리가 아니라 직선거리다
 * @param alternatives 대표 말고 이 지역에 닿는 다른 수단들. 없으면 빈 목록이다
 * @param departures 그날 탈 수 있는 편들(#414) — 몇 시 차인가. <b>비어 있는 것이 정상</b>이다:
 *     버스·여객선은 여행일이 조회창(오늘~+2일, 여객선 +7일) 밖이면 물을 수 없고, 열차도 그날 운행이
 *     없거나 막차가 지났으면 빈다. 화면은 그때 시간표 줄만 접고 소요시간으로 그린다
 */
@Builder(toBuilder = true)
public record RegionAccess(
        TransitMode mode,
        Status status,
        String fromName,
        String toName,
        Coordinate toPoint,
        TrainLeg fastest,
        Integer durationMinutes,
        Integer distanceKm,
        List<TransitOption> alternatives,
        List<Departure> departures) {

    public RegionAccess {
        Objects.requireNonNull(mode, "수단은 null 일 수 없습니다.");
        Objects.requireNonNull(status, "접근 상태는 null 일 수 없습니다.");
        // null 을 그대로 두면 화면과 테스트가 매번 null 검사를 한다. 없는 것은 빈 목록이다.
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        departures = departures == null ? List.of() : List.copyOf(departures);
    }

    public enum Status {
        /**
         * 갈 수 있고 <b>언제 닿는지도</b> 안다.
         *
         * <p>근거는 수단마다 다르다 — 열차는 실제 운행 편에서, 자차는 출발 시각에 이동시간을 얹어
         * 안다(#379). 버스·여객선은 시간표를 못 물어 여기 오지 않는다({@link #POINT_ONLY}).
         */
        AVAILABLE,
        /** 출발지·지역 어느 쪽에도 닿는 지점이 없어 대중교통으로 갈 수 없음. */
        NO_STATION,
        /** 지점은 있으나 그 날짜에 운행이 없음. */
        NO_SERVICE_ON_DATE,
        /** 조회 실패(키 없음·외부 오류). */
        UNAVAILABLE,
        /**
         * 도착 지점만 앎 — 시각은 <b>아직</b> 모른다(#97).
         *
         * <p>버스·여객선의 구간 조회는 오늘~+2일(여객선 +7일)만 답한다. 연차 기반으로 다음 달 코스를 짜는
         * 서비스라 요청 시점에 시간표를 물을 수 없어, 지금은 지점까지만 안다. 구간 소요시간을 저장하면
         * (#107) 여기에 시각이 붙는다.
         *
         * <p>{@link #NO_SERVICE_ON_DATE} 와 구분한다 — 그쪽은 "물어봤더니 없다", 이쪽은 "아직 안 물었다" 다.
         * 같은 값으로 뭉치면 로그에서 외부 장애와 미구현이 섞인다.
         */
        POINT_ONLY
    }

    /**
     * 지역에 닿는 지점 — 코스 첫 장소를 고르는 기준점. 지점이 해석되기만 하면 <b>운행·조회 결과와 무관하게</b> 답한다.
     *
     * <p>그날 운행이 없거나 조회가 실패해도, 그 지역에 그 수단으로 간다면 내리는 곳은 그 지점이다. 여기서 빈 값을 주면
     * 호출자가 출발지 좌표로 되돌아가 반대편 동선을 짠다.
     */
    public Optional<Coordinate> arrivalPoint() {
        return Optional.ofNullable(toPoint);
    }

    /** 지역에 닿는 시각 — 실제 운행 편을 찾았을 때만 안다. 1일차에 어느 시간대부터 일정을 넣을지의 근거다. */
    public Optional<LocalDateTime> arrivalAt() {
        return Optional.ofNullable(fastest).map(TrainLeg::arriveAt);
    }

    /**
     * 집을 나서는 시각을 알 때의 도착 시각(#107). 운행 편을 찾았으면 그 편의 도착 시각을, 아니면 저장해 둔
     * 소요시간을 얹어 만든다. 둘 다 모르면 빈 값이다.
     *
     * <p><b>기다리는 시간은 안 들어 있다.</b> 버스·여객선은 시간표를 못 물어(조회창 +2일·+7일) 다음 편까지의
     * 대기를 알 수 없다. 그래서 이 값은 <b>가장 이른 도착</b>이고 실제로는 더 늦을 수 있다.
     *
     * <p>그래도 쓰는 이유는 대안이 "하루 전부" 이기 때문이다 — 서울에서 세 시간 걸려 닿는 지역에 오전
     * 일정을 넣는 것보다, 조금 이르게 잡더라도 이동시간을 반영하는 쪽이 지킬 수 있는 코스에 가깝다. 자차가
     * 이미 같은 방식으로 계산한다({@code carFirstDayStart}).
     */
    public Optional<LocalDateTime> arrivalAt(LocalDate date, LocalTime departure) {
        Objects.requireNonNull(date, "여행일은 null 일 수 없습니다.");
        Objects.requireNonNull(departure, "출발 시각은 null 일 수 없습니다.");
        return arrivalAt()
                .or(() -> Optional.ofNullable(durationMinutes).map(minutes -> date.atTime(departure)
                        .plusMinutes(minutes)));
    }

    /** 저장해 둔 소요시간을 얹은 사본. 값이 그대로면 자기 자신을 준다 — 불필요한 객체를 만들지 않는다. */
    public RegionAccess withDuration(Integer minutes) {
        if (Objects.equals(durationMinutes, minutes)) {
            return this;
        }
        return toBuilder().durationMinutes(minutes).build();
    }

    /** 출발지에서 도착 지점까지의 거리를 얹은 사본(#379). 지점을 고른 뒤라야 잴 수 있어 따로 붙인다. */
    public RegionAccess withDistanceKm(Integer km) {
        if (Objects.equals(distanceKm, km)) {
            return this;
        }
        return toBuilder().distanceKm(km).build();
    }

    /** 대안 목록을 얹은 사본. 대표를 고른 뒤 나머지를 붙이는 자리다. */
    public RegionAccess withAlternatives(List<TransitOption> others) {
        return toBuilder().alternatives(others).build();
    }

    public static RegionAccess available(
            String fromName, String toName, Coordinate toPoint, TrainLeg fastest, List<Departure> departures) {
        return RegionAccess.builder()
                .mode(TransitMode.TRAIN)
                .status(Status.AVAILABLE)
                .fromName(fromName)
                .toName(toName)
                .toPoint(toPoint)
                .fastest(fastest)
                .departures(departures)
                .build();
    }

    /** 시간표를 갈아 끼운다 — 버스·여객선은 도착 지점을 정한 뒤에야 어느 구간을 물을지 알 수 있다(#414). */
    public RegionAccess withDepartures(List<Departure> departures) {
        return toBuilder().departures(departures).build();
    }

    /**
     * 자차로 지역까지(#379). 도착 지점이 <b>지역 그 자체</b>라 역·터미널을 해석할 것이 없다.
     *
     * <p><b>출발 지점명은 받아서 그대로 싣는다</b>(#382). 서버는 좌표를 이름으로 바꾸지 못하므로 지어내지
     * 않는다 — 앱이 저장할 때 실어 보낸 값이 여기까지 온다. 모르면 null 이고 화면은 그 조각만 접는다.
     *
     * <p>상태가 {@link Status#AVAILABLE} 인 이유는 <b>언제 닿는지를 알기 때문</b>이다. 열차처럼 운행 편이
     * 있어서가 아니라, 자차는 나서는 시각에 이동시간을 얹으면 그대로 도착 시각이 된다.
     */
    public static RegionAccess car(
            String originName, String regionName, Coordinate region, int durationMinutes, Integer distanceKm) {
        Objects.requireNonNull(region, "지역 좌표는 null 일 수 없습니다.");
        return RegionAccess.builder()
                .mode(TransitMode.CAR)
                .status(Status.AVAILABLE)
                .fromName(originName)
                .toName(regionName)
                .toPoint(region)
                .durationMinutes(durationMinutes)
                .distanceKm(distanceKm)
                .build();
    }

    /** 닿는 지점 자체가 없는 경우 — 도착 지점도 없다. */
    public static RegionAccess noStation(String fromName, String toName) {
        return RegionAccess.builder()
                .mode(TransitMode.TRAIN)
                .status(Status.NO_STATION)
                .fromName(fromName)
                .toName(toName)
                .build();
    }

    /** 지점은 해석됐으나 그날 운행이 없는 경우 — 시각은 모르지만 <b>지점은 안다</b>. */
    public static RegionAccess noServiceOnDate(String fromName, String toName, Coordinate toPoint) {
        return RegionAccess.builder()
                .mode(TransitMode.TRAIN)
                .status(Status.NO_SERVICE_ON_DATE)
                .fromName(fromName)
                .toName(toName)
                .toPoint(toPoint)
                .build();
    }

    /** 지점은 해석됐으나 조회가 실패한 경우 — 해석된 지점명·좌표는 그대로 담는다(해석과 조회는 별개). */
    public static RegionAccess unavailable(String fromName, String toName, Coordinate toPoint) {
        return RegionAccess.builder()
                .mode(TransitMode.TRAIN)
                .status(Status.UNAVAILABLE)
                .fromName(fromName)
                .toName(toName)
                .toPoint(toPoint)
                .build();
    }

    /**
     * 버스·여객선으로 내리는 지점만 아는 경우(#97). 출발 지점은 담지 않는다 — 이 값을 만드는 시점에는 도착 쪽만
     * 해석했고, 없는 것을 빈 문자열로 채우면 화면이 "출발: " 로 뜬다.
     */
    /**
     * 이 결과와 다른 수단의 도착 지점 후보를 견줘 <b>지역에 더 가까운 쪽</b>으로 바꾼다(#97).
     *
     * <p><b>운행 편을 찾았으면 바꾸지 않는다.</b> 실제 시각까지 아는 결과는 이것뿐이라, 조금 더 가까운 지점을
     * 얻자고 도착 시각을 버리면 첫날 일정이 다시 "하루 전부" 로 돌아간다 — 얻는 것보다 잃는 것이 크다.
     * 소요시간을 저장하면(#107) 그때는 같은 축에서 견줄 수 있다.
     *
     * <p>바꾸는 경우는 둘이다. 열차역이 아예 없거나({@link Status#NO_STATION}), 역은 있는데 그날 운행이
     * 없거나 조회가 실패했고 <b>그 역이 터미널·항구보다 먼</b> 경우다. 후자는 실제로 흔하다 — 양양·합천·태안·
     * 진도·완도·함양처럼 최근접 역이 30~50㎞ 밖인 지역이 아홉 곳이고, 그런 곳의 시외버스 터미널은 읍내에 있다.
     *
     * <p>이긴 후보가 원래 지점이면 <b>이 결과를 그대로</b> 돌려준다. {@code POINT_ONLY} 로 갈아치우면
     * "그날 열차 없음"·"조회 실패" 라는 사유가 사라져, 외부 장애가 화면에서 조용해진다.
     */
    public RegionAccess orNearer(Coordinate region, RegionArrival... others) {
        Objects.requireNonNull(region, "지역 좌표는 null 일 수 없습니다.");
        if (status == Status.AVAILABLE) {
            return this;
        }
        Optional<RegionArrival> best = RegionArrival.nearestTo(region, others);
        if (best.isEmpty()) {
            return this;
        }
        if (toPoint == null) {
            return pointOnly(best.get()); // 내 지점이 없으니 비교할 것도 없다
        }
        double mineKm = region.haversineKmTo(toPoint);
        double bestKm = region.haversineKmTo(best.get().point());
        return bestKm < mineKm ? pointOnly(best.get()) : this;
    }

    public static RegionAccess pointOnly(RegionArrival arrival) {
        Objects.requireNonNull(arrival, "도착 지점은 null 일 수 없습니다.");
        return RegionAccess.builder()
                .mode(arrival.mode())
                .status(Status.POINT_ONLY)
                .toName(arrival.name())
                .toPoint(arrival.point())
                .build();
    }
}
