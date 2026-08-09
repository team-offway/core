package com.offway.core.itinerary.domain;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자동 생성된 여행 코스(애그리거트 루트) — 한 지역의 날짜별 타임라인. 코스 생성(#30)이 조립하고, 저장·조회(#33)가 영속화한다.
 *
 * <p>지역은 다른 도메인(trip/region)의 레퍼런스라 raw {@code regionId} 로만 참조하고, 하루 일정({@link DaySchedule})은
 * 생명주기를 공유하는 애그리거트 내부라 {@code @OneToMany} 로 함께 다룬다. 혜택·비용은 정책 매칭 결과라 응답 시점에 계산해 붙인다
 * (도메인 상태 제외 — 저장 코스가 정책 변경에 뒤처지지 않게).
 */
@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    /** 코스 상한 — 최대 2박3일(feature-spec F4 · 와이어프레임 캘린더 정책). */
    public static final int MAX_TRAVEL_DAYS = 3;

    /** 게스트 ID 최대 길이 — {@code guest_id} 컬럼 폭과 일치시켜, 초과 입력이 저장 단계 서버 오류로 새지 않게 경계에서 거른다. */
    public static final int MAX_GUEST_ID_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 추천 지역(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /**
     * 여행 기간(1~3) — <b>달력으로 며칠짜리 여행인지</b>. 2박3일이면 3이다.
     *
     * <p><b>일정이 있는 날의 수가 아니다.</b> 예전에는 {@code days.size()} 를 넣었는데, 일정이 없는 날은 코스에서
     * 빠지므로(#159) 첫날이 이동만 있는 여행에서 이 값이 실제 기간보다 짧아졌다. 그러면 {@link #travelEndDate()}
     * 가 하루 이르게 나오고, 그 종료일로 <b>연차가 하루 덜 차감됐다</b>(#164).
     *
     * <p>표시 일수가 필요하면 {@code days.size()} 로 그때 세면 된다 — 저장할 이유가 없는 파생값이다.
     */
    @Column(name = "travel_days", nullable = false)
    private int travelDays;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Density density;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TransportMode transport;

    /**
     * 여행 시작일(저장 코스만). 생성만 된 코스와 이 컬럼 이전에 저장된 코스는 null 이다.
     *
     * <p>연차 차감 일수를 <b>서버가 다시 계산</b>하는 근거다 — 차감 시점에 클라이언트가 날짜를 보내면 보낸 만큼
     * 차감량이 바뀐다.
     */
    @Column(name = "travel_date")
    private LocalDate travelDate;

    /** 소유 게스트 ID(저장된 코스만) — 로그인 전이라 클라이언트 게스트 식별자로 "내 코스"를 묶는다. 생성만 된 코스는 null. */
    @Column(name = "guest_id", length = MAX_GUEST_ID_LENGTH)
    private String guestId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(
            name = "course_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @OrderBy("dayNumber")
    private List<DaySchedule> days;

    private Course(
            String guestId,
            Long regionId,
            Density density,
            TransportMode transport,
            List<DaySchedule> days,
            LocalDate travelDate,
            int travelDays) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("코스에는 하루 이상이 있어야 합니다");
        }
        if (days.size() > MAX_TRAVEL_DAYS) {
            throw new IllegalArgumentException("코스는 최대 " + MAX_TRAVEL_DAYS + "일까지입니다: " + days.size());
        }
        requireSequentialDays(days);
        requireIncreasingOffsets(days);
        requireSpanCovers(days, travelDays);
        this.guestId = guestId;
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.density = Objects.requireNonNull(density, "일정 밀도는 필수입니다");
        this.transport = Objects.requireNonNull(transport, "이동수단은 필수입니다");
        this.days = List.copyOf(days);
        this.travelDays = travelDays;
        this.travelDate = travelDate;
    }

    /**
     * 하루 일정들을 묶어 코스를 만든다(생성용, 소유자 없음). 일수 상한(2박3일)과 일차 연속성을 스스로 검증한다.
     *
     * <p>여행 날짜를 함께 담는다 — 화면이 {@code day 1  5.1/금} 을 그리려면 며칠째가 몇 일인지 알아야 하고,
     * 그 근거가 여기서 끊기면 생성 응답의 날짜가 통째로 빈다(#141).
     */
    public static Course of(
            Long regionId,
            Density density,
            TransportMode transport,
            List<DaySchedule> days,
            LocalDate travelDate,
            int travelDays) {
        return new Course(null, regionId, density, transport, days, travelDate, travelDays);
    }

    /** 게스트 소유로 코스를 만든다(저장용). 게스트 ID 는 공백일 수 없고 길이 상한을 넘지 않는다(빈 값이면 모든 요청이 한 묶음을 공유). */
    public static Course ownedBy(
            String guestId,
            Long regionId,
            Density density,
            TransportMode transport,
            List<DaySchedule> days,
            LocalDate travelDate,
            int travelDays) {
        Objects.requireNonNull(guestId, "게스트 ID는 필수입니다");
        if (guestId.isBlank()) {
            throw new IllegalArgumentException("게스트 ID는 비어 있을 수 없습니다");
        }
        if (guestId.length() > MAX_GUEST_ID_LENGTH) {
            throw new IllegalArgumentException("게스트 ID가 너무 깁니다: " + guestId.length());
        }
        return new Course(guestId, regionId, density, transport, days, travelDate, travelDays);
    }

    /**
     * 여행 종료일 — 시작일에서 기간만큼. 1박2일이면 시작일 다음 날이다.
     *
     * <p>날짜가 없는 코스에서는 부를 수 없다. 부재가 계약(400)이라 {@link #requireTravelDate()} 로 먼저 거른다.
     *
     * <p><b>{@code days} 컬렉션을 보지 않는다.</b> {@code @OneToMany} 가 LAZY 라 트랜잭션 밖에서 건드리면
     * 터지는데, 연차 차감 경로가 코스를 조회한 뒤 트랜잭션 밖에서 종료일을 묻는다. 기간을 컬럼으로 들고 있는
     * 이유가 이것이다(#164).
     */
    public LocalDate travelEndDate() {
        if (travelDate == null) {
            throw new IllegalStateException("여행 날짜가 없는 코스의 종료일을 물었습니다: id=" + id);
        }
        return travelDate.plusDays(travelDays - 1L);
    }

    /**
     * 이 여행이 {@code today} 기준으로 끝났는가 — 종료일이 오늘보다 <b>이전</b>이어야 한다.
     *
     * <p>종료 당일은 끝난 것으로 보지 않는다. 아직 여행 중일 수 있다.
     *
     * <p>여행 날짜가 없으면 끝났는지 알 수 없으므로 거짓이다 — 모르는 것을 "끝났다" 로 답하면 안 된다.
     */
    public boolean hasEndedBy(LocalDate today) {
        return travelDate != null && travelEndDate().isBefore(today);
    }

    /**
     * 연차를 차감하려면 여행 날짜가 있어야 한다 — <b>도메인이 스스로 막는다.</b>
     *
     * <p>이 컬럼이 생기기 전에 저장된 코스가 있으므로 <b>멀쩡한 클라이언트가 정상 요청으로 닿을 수 있다</b>. 불변식이
     * 아니라 계약이라 400 이다.
     */
    public void requireTravelDate() {
        if (travelDate == null) {
            throw ItineraryException.travelDateMissing();
        }
    }

    /** 코스 전체 슬롯(장소) 수. */
    public int totalSlots() {
        return days.stream().mapToInt(DaySchedule::slotCount).sum();
    }

    /**
     * 며칠째가 실제로 몇 월 며칠인지(#141). 화면이 {@code day 1  7.26/토} 를 그리는 재료다.
     *
     * <p>프론트가 더할 수도 있지만 서버가 답한다 — 이미 같은 날짜로 날씨·혜택을 매칭하고 있어, 계산 주체가
     * 둘로 갈리면 어긋날 여지가 생긴다.
     *
     * @param travelDate 여행 시작일. 날짜 없이 저장된 코스(#111 이전)는 null 이다
     * @param dayNumber 며칠째(1부터)
     * @return 그날의 날짜. 시작일을 모르면 null — 없는 것을 지어내지 않는다
     */
    public static LocalDate dateOfDay(LocalDate travelDate, int dayNumber) {
        if (travelDate == null) {
            return null;
        }
        return travelDate.plusDays(dayNumber - 1L);
    }

    /**
     * 목록 카드에 쓸 대표 이미지 — 첫 슬롯의 것(#171).
     *
     * <p>이미지가 없는 슬롯은 건너뛴다. 하나도 없으면 빈 Optional 이고 화면은 자리표시자를 쓴다.
     */
    public Optional<String> coverImageUrl() {
        return days.stream()
                .flatMap(day -> day.getSlots().stream())
                .map(Slot::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst();
    }

    /**
     * 코스의 중심 좌표 — 날씨를 어느 지점으로 물을지의 기준(#169).
     *
     * <p>슬롯 좌표의 평균이다. 생성 경로는 볼거리 군집의 중심(hub)을 쓰는데, 저장 코스는 그 계산을 다시 하지
     * 않고 이미 담긴 슬롯으로 구한다 — 날씨는 격자 단위라 이 정도 차이가 결과를 바꾸지 않는다.
     *
     * <p>좌표 없는 슬롯은 제외한다. 하나도 없으면 빈 Optional.
     */
    public Optional<Coordinate> center() {
        List<Slot> located = days.stream()
                .flatMap(day -> day.getSlots().stream())
                .filter(slot -> slot.getLat() != null && slot.getLng() != null)
                .toList();
        if (located.isEmpty()) {
            return Optional.empty();
        }
        double lat = located.stream().mapToDouble(Slot::getLat).average().orElseThrow();
        double lng = located.stream().mapToDouble(Slot::getLng).average().orElseThrow();
        return Optional.of(new Coordinate(lat, lng));
    }

    /**
     * 이 하루가 실제로 몇 월 며칠인지 — <b>표시 번호가 아니라 달력 오프셋</b>으로 센다.
     *
     * <p>일정이 없는 날은 코스에서 빠지므로 표시 번호와 달력 위치가 어긋날 수 있다. 그때 표시 번호로
     * 날짜를 세면 하루가 앞당겨진다(#159).
     *
     * @return 그날의 날짜. 여행 시작일을 모르면 null — 없는 것을 지어내지 않는다
     */
    public LocalDate dateOf(DaySchedule schedule) {
        if (travelDate == null) {
            return null;
        }
        return travelDate.plusDays(schedule.getDayOffset());
    }

    /**
     * 여행 기간이 실제 일정을 담을 수 있는지 — <b>기간 밖에 놓인 하루가 있으면 안 된다.</b>
     *
     * <p>일차 오프셋이 기간을 넘으면 종료일보다 뒤에 일정이 있다는 뜻이라 앞뒤가 맞지 않는다. 기간을 잘못
     * 넘기면 여기서 걸린다 — 조용히 통과하면 연차 차감이 그만큼 어긋난다.
     */
    private static void requireSpanCovers(List<DaySchedule> days, int travelDays) {
        if (travelDays < 1 || travelDays > MAX_TRAVEL_DAYS) {
            throw new IllegalArgumentException("여행 기간은 1~" + MAX_TRAVEL_DAYS + "일이어야 합니다: " + travelDays);
        }
        int lastOffset = days.stream().mapToInt(DaySchedule::getDayOffset).max().orElseThrow();
        if (lastOffset >= travelDays) {
            throw new IllegalArgumentException(
                    "여행 기간 밖에 일정이 있습니다: 기간=" + travelDays + "일, 마지막 일정=" + (lastOffset + 1) + "일째");
        }
    }

    /**
     * 일차 순서대로 <b>달력도 앞으로만</b> 가는지 — 오프셋이 엄격히 증가해야 한다.
     *
     * <p>{@link #requireSpanCovers}는 최대 오프셋만 보므로 순서를 보지 못한다. 1일차에 오프셋 1, 2일차에 0을
     * 넣어도 3일 기간이면 통과하는데, 그러면 화면 순서와 {@link #dateOf} 날짜 순서가 <b>역전</b>된다.
     *
     * <p>같은 오프셋 둘도 여기서 걸린다 — 하루를 두 번 쓰는 셈이라 그 날짜에 무엇이 있는지 답할 수 없다.
     */
    private static void requireIncreasingOffsets(List<DaySchedule> days) {
        for (int i = 1; i < days.size(); i++) {
            int previous = days.get(i - 1).getDayOffset();
            int current = days.get(i).getDayOffset();
            if (current <= previous) {
                throw new IllegalArgumentException(
                        "일차가 갈수록 날짜도 뒤여야 합니다: " + days.get(i - 1).getDayNumber() + "일차=시작+" + previous
                                + "일, " + days.get(i).getDayNumber() + "일차=시작+" + current + "일");
            }
        }
    }

    private static void requireSequentialDays(List<DaySchedule> days) {
        for (int i = 0; i < days.size(); i++) {
            int expected = i + 1;
            if (days.get(i).getDayNumber() != expected) {
                throw new IllegalArgumentException(
                        "일차가 1부터 연속이어야 합니다: " + expected + " 위치에 " + days.get(i).getDayNumber());
            }
        }
    }
}
