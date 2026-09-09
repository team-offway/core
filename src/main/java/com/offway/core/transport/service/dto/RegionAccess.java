package com.offway.core.transport.service.dto;

import com.offway.core.common.geo.Coordinate;
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
 * @param chosen 코스가 탈 편 — <b>가장 일찍 닿는</b> 편이다({@link Status#AVAILABLE} 일 때만, 아니면 null).
 *     소요시간이 가장 짧은 편이 아니다(#442)
 * @param durationMinutes 소요시간(분, 모르면 null). 버스·여객선은 저장해 둔 구간 측정값에서, 자차는
 *     출발지→지역 이동시간에서 온다. 시간표를 못 묻는 수단이라 <b>시각 대신 이것으로</b> 도착 시각을
 *     만든다(#107 · #379)
 * @param distanceKm 출발지에서 도착 지점까지의 직선거리(㎞, 모르면 null). 화면이 "약 2시간 29분 · 200km"
 *     로 소요시간 옆에 붙인다(#379). 실제 주행거리가 아니라 직선거리다
 * @param alternatives 대표 말고 이 지역에 닿는 다른 수단들. 없으면 빈 목록이다
 * @param viaName 갈아타는 지점명(#508). 직통이 없어 허브를 한 번 경유할 때만 채워진다 — 없으면 null 이고,
 *     그때는 출발에서 도착까지 바로 간다는 뜻이다
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
        TrainLeg chosen,
        Integer durationMinutes,
        Integer distanceKm,
        String viaName,
        List<TransitOption> alternatives,
        List<Departure> departures) {

    public RegionAccess {
        Objects.requireNonNull(mode, "수단은 null 일 수 없습니다.");
        Objects.requireNonNull(status, "접근 상태는 null 일 수 없습니다.");
        // **지점 이름에 종류를 붙인다.** 마스터 이름이 그대로 나가면 코스 첫 칸이 "태안" 이 되는데,
        // 태안 어디로 가라는 것인지 알 수 없다 — 열차면 태안역, 버스면 태안터미널이다.
        //
        // 여기서 붙이는 이유는 **읽는 곳이 여럿**이라서다(코스 슬롯·교통 카드·대안 목록). 읽는 쪽마다
        // 붙이면 한 곳을 빠뜨렸을 때 화면에서만 조용히 다르게 보인다. 규칙은 TransitMode 가 소유한다.
        fromName = mode.placeName(fromName);
        toName = mode.placeName(toName);
        viaName = mode.placeName(viaName);
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
        /**
         * 지점은 있는데 <b>그 구간에 노선이 없다</b>(#508) — 경유할 길도 못 찾았다.
         *
         * <p>{@link #NO_SERVICE_ON_DATE} 와 갈라야 한다. 그쪽은 "그날 차가 없다"(다른 날은 있다)이고
         * 이쪽은 "이 두 지점을 잇는 노선이 없다" 다. 사용자가 할 일이 다르다 — 전자는 날짜를 바꾸면 되고
         * 후자는 다른 수단을 봐야 한다.
         *
         * <p><b>왜 필요했나.</b> 예전에는 여기서도 {@code POINT_ONLY}("아직 안 물었다")로 답했다. 그래서
         * 서울에서 봉화까지 고속버스로 가라는 안내가 나갔다 — 봉화행 노선은 어디에도 없는데. <b>없는 길을
         * 안내하는 것은 아무 안내도 안 하는 것보다 나쁘다.</b>
         */
        NO_ROUTE,
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
        POINT_ONLY,

        /**
         * <b>계산할 근거가 없다</b> — 저장할 때 출발지를 안 받았다(#422).
         *
         * <p>결과가 아니라 입력(출발 좌표)을 저장해 두고 상세에서 계산하는데, 그 좌표가 없으면 되살릴
         * 방법이 없다. {@code POST /courses} 의 필수 필드가 아니라 <b>좌표 없이도 저장이 성공</b>하고,
         * 그렇게 저장된 코스는 상세에서만 도착 정보가 사라졌다.
         *
         * <p>다른 상태와 다른 점 — 이건 <b>외부나 노선의 사정이 아니라 우리 데이터가 빈 것</b>이다.
         * {@link #UNAVAILABLE}(조회 실패)과 섞으면 외부 장애를 찾다가 시간을 버린다.
         *
         * <p>이 상태를 만든 이유는 <b>필드가 통째로 빠지던 것</b>을 없애기 위해서다. 없으면 앱이
         * "이 값을 모르는 옛 서버" 와 "서버가 답을 못 하는 코스" 를 구분할 수 없다.
         */
        ORIGIN_UNKNOWN
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

    /**
     * 지역에 닿는 시각 — 실제 운행 편을 찾았을 때만 안다. 1일차에 어느 시간대부터 일정을 넣을지의 근거다.
     *
     * <p>근거는 둘이다. 열차는 {@code chosen}(고른 편)에서, <b>버스·여객선은 시간표의 첫 편</b>에서
     * 온다(#422). 뒤쪽이 없던 시절에는 그 둘이 소요시간으로만 답했는데, #414 로 시간표가 붙으면서
     * 실제 도착 시각을 알게 됐다.
     *
     * <p>{@code departures} 는 이미 "탈 수 있는 편만, 이른 순" 이라 첫 편이 곧 가장 이른 도착이다.
     */
    public Optional<LocalDateTime> arrivalAt() {
        if (chosen != null) {
            return Optional.of(chosen.arriveAt());
        }
        return departures.stream().findFirst().map(Departure::arriveAt);
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

    /**
     * 출발 지점명을 얹은 사본(#396) — <b>어디서 타는가</b>.
     *
     * <p>버스·여객선은 도착 지점을 정한 뒤에야 출발 쪽을 해석할 수 있다. 고속·시외는 코드 공간이
     * 갈려 있어 <b>도착 터미널과 같은 종류</b>로 찾아야 하고, 여객선도 마찬가지다 — 그래서 지점을
     * 고르는 {@code pointOnly} 시점에는 아직 모른다.
     *
     * <p>모르면 null 그대로 둔다. 지어내지 않고 화면이 그 조각만 접는다.
     */
    public RegionAccess withFromName(String fromName) {
        if (Objects.equals(this.fromName, fromName)) {
            return this;
        }
        return toBuilder().fromName(fromName).build();
    }

    /** 출발지에서 도착 지점까지의 거리를 얹은 사본(#379). 지점을 고른 뒤라야 잴 수 있어 따로 붙인다. */
    /**
     * 갈아타는 지점을 단다(#508). 소요시간은 두 구간의 합이라 함께 받는다.
     *
     * <p>상태는 {@link Status#POINT_ONLY} 로 둔다 — 얼마나 걸리는지는 알지만 <b>몇 시 차인지는 모른다.</b>
     * 두 구간의 시간표를 이으려면 환승 대기까지 맞춰야 하는데, 버스 시간표는 오늘~+2일만 답해서 다음 달
     * 코스에는 애초에 없는 정보다.
     */
    public RegionAccess withVia(String viaName, Integer totalMinutes) {
        // **시간표를 비운다.** 여기 실려 있던 것은 <b>직통 구간</b>의 편들이다 — 그 구간은 안 다니는
        // 것으로 판명돼 경유로 넘어온 참이라, 그대로 두면 "대전복합 경유" 라고 말하면서 서울→무주
        // 직통 시각을 함께 보여주게 된다. 두 구간의 환승 대기를 맞출 수 없어 대신 채울 것도 없다.
        return toBuilder()
                .viaName(viaName)
                .durationMinutes(totalMinutes)
                .status(Status.POINT_ONLY)
                .departures(List.of())
                .build();
    }

    /** 그 구간에 노선이 없다고 답한다(#508) — 지점은 그대로 두고 상태만 바꾼다. */
    public RegionAccess withoutRoute() {
        return toBuilder().status(Status.NO_ROUTE).durationMinutes(null).departures(List.of()).build();
    }

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
            String fromName, String toName, Coordinate toPoint, TrainLeg chosen, List<Departure> departures) {
        return RegionAccess.builder()
                .mode(TransitMode.TRAIN)
                .status(Status.AVAILABLE)
                .fromName(fromName)
                .toName(toName)
                .toPoint(toPoint)
                .chosen(chosen)
                .departures(departures)
                .build();
    }

    /**
     * 시간표를 갈아 끼운다 — 버스·여객선은 도착 지점을 정한 뒤에야 어느 구간을 물을지 알 수 있다(#414).
     *
     * <p><b>시간표가 붙으면 상태도 함께 올린다</b>(#422). {@link Status#POINT_ONLY} 는 "아직 안 물었다"
     * 는 뜻인데, 물어서 편이 나왔는데도 그대로 두면 <b>상태와 값이 서로를 부정한다</b> — 실제로
     * {@code status=POINT_ONLY} 인데 우등 두 편이 실려 나갔다.
     *
     * <p>화면은 목록만 보고 그려서 멀쩡했지만, 상태를 믿는 쪽(첫날 재정렬·로그)이 나중에 어긋난다.
     *
     * <p>빈 목록이면 올리지 않는다 — 물어봤는데 없는 것과 못 물은 것은 여전히 다르고, 여기서는
     * 그 둘을 가릴 근거가 없다(조회창 밖이면 아예 안 물었다).
     */
    public RegionAccess withDepartures(List<Departure> departures) {
        RegionAccessBuilder builder = toBuilder().departures(departures);
        if (status == Status.POINT_ONLY && departures != null && !departures.isEmpty()) {
            builder.status(Status.AVAILABLE);
        }
        return builder.build();
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
     * <p><b>수단이 아니라 "그 지역에 닿는가" 를 먼저 본다</b>(#542). 예전에는 운행 편을 찾았으면
     * ({@link Status#AVAILABLE}) 무조건 그것으로 끝냈다. 실제 시각을 아는 결과가 그것뿐이었기 때문인데,
     * 그 규칙이 <b>읍내 터미널을 두고 20~78㎞ 밖 역으로 보냈다</b> — 태안이 37.7㎞ 밖 홍성역, 고성(강원)이
     * 78.2㎞ 밖 강릉역이다. 코스는 내린 곳을 지역 안 동선의 기준점으로 쓰므로(#127), 기준점이 그만큼
     * 밖이면 첫날 동선 전체가 틀어진다. 89곳 전수로 재니 그런 곳이 <b>20곳</b>이었다.
     *
     * <p>그래서 세 갈래다.
     *
     * <ul>
     *   <li><b>내가 지역 안이면 그대로</b> — 직통이다. 공주역(13.0㎞)처럼 역이 지역 안인 곳은 안 바뀐다.
     *   <li><b>내가 밖인데 상대가 안이면 넘긴다</b> — 도착 시각을 잃더라도 기준점이 맞는 쪽이 낫다.
     *   <li><b>둘 다 밖이면 예전 규칙</b> — 운행 편을 찾았으면 지킨다. 산청(32.7㎞ 대 17.3㎞)·창녕처럼
     *       어느 쪽도 지역에 못 닿는 곳에서 도착 시각까지 버릴 이유는 없다.
     * </ul>
     *
     * <p>운행 편을 못 찾은 결과({@link Status#NO_STATION} 등)는 예전처럼 <b>더 가까운 쪽</b>이 이긴다.
     * 잃을 도착 시각이 없으니 거리만 보면 된다.
     *
     * <p>이긴 후보가 원래 지점이면 <b>이 결과를 그대로</b> 돌려준다. {@code POINT_ONLY} 로 갈아치우면
     * "그날 열차 없음"·"조회 실패" 라는 사유가 사라져, 외부 장애가 화면에서 조용해진다.
     */
    public RegionAccess orNearer(Coordinate region, RegionArrival... others) {
        Objects.requireNonNull(region, "지역 좌표는 null 일 수 없습니다.");
        Optional<RegionArrival> best = RegionArrival.nearestTo(region, others);
        if (best.isEmpty()) {
            return this;
        }
        if (toPoint == null) {
            return pointOnly(best.get()); // 내 지점이 없으니 비교할 것도 없다
        }
        double mineKm = region.haversineKmTo(toPoint);
        double bestKm = region.haversineKmTo(best.get().point());
        if (status == Status.AVAILABLE) {
            // 운행 편을 찾았어도 **내가 지역 밖이고 상대가 지역 안이면** 넘긴다(#542).
            return reachesRegion(mineKm) || !reachesRegion(bestKm) ? this : pointOnly(best.get());
        }
        return bestKm < mineKm ? pointOnly(best.get()) : this;
    }

    /**
     * 이 거리면 <b>그 지역에 닿은 것</b>으로 본다 — 직통인가를 가르는 선이다(#542).
     *
     * <p>89곳 전수로 재 보면 숫자가 한쪽으로 뚜렷하게 갈린다. 읍내 터미널은 지역 중심에서 0.2~1.5㎞ 이고,
     * 지역 밖 역은 20~78㎞ 다. 그 사이가 거의 비어 있어 어디를 잘라도 같은 20곳이 걸린다.
     *
     * <p><b>15 를 고른 이유</b>는 반대쪽이다 — 역이 지역 안인 곳을 건드리지 않아야 한다. 공주역이 공주시
     * 중심에서 13.0㎞ 라 가장 아슬아슬하고, 그 위로 안전하게 남기는 값이다. 군 단위 반경이 대략 10~20㎞ 라
     * 중심에서 15㎞ 면 대개 같은 시군이기도 하다.
     *
     * <p>경계 폴리곤이 아니라 중심 좌표를 쓰는 것은 우리가 가진 것이 그것뿐이기 때문이다.
     */
    private static final double REACHES_REGION_KM = 15.0;

    private static boolean reachesRegion(double km) {
        return km <= REACHES_REGION_KM;
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
