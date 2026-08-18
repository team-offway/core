package com.offway.core.itinerary.domain;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import jakarta.persistence.CascadeType;
import com.offway.core.leave.domain.StartDayLeave;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
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
import java.util.ArrayList;
import java.util.Collections;
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

    /** 거리를 미터로 내리기 위한 환산 — 슬롯 사이 거리와 같은 단위다. */
    private static final int METERS_PER_KM = 1000;

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

    /**
     * 출발지 위도 — 대중교통 열차 접근을 <b>다시 계산</b>하려고 둔다(#187).
     *
     * <p>계산 결과가 아니라 입력을 저장한다. 열차 시간표는 바뀌므로 생성 시점의 시간을 그대로 보관하면
     * 한 달 뒤 여행에서 낡은 시간을 보여주게 되고, 그건 없는 것보다 나쁘다.
     *
     * <p>이 필드가 생기기 전 코스와 자차 코스는 null 이다.
     */
    @Column(name = "origin_lat")
    private Double originLat;

    /** 출발지 경도 — {@link #originLat} 와 짝. */
    @Column(name = "origin_lng")
    private Double originLng;

    /** 소유 게스트 ID(저장된 코스만) — 로그인 전이라 클라이언트 게스트 식별자로 "내 코스"를 묶는다. 생성만 된 코스는 null. */
    @Column(name = "guest_id", length = MAX_GUEST_ID_LENGTH)
    private String guestId;

    /**
     * 첫날에 쓴 연차 — <b>출발 시각의 근거</b>(#138).
     *
     * <p>상세 조회와 날짜 수정이 열차 접근을 다시 계산한다. 이 값이 없으면 생성은 반차(12시) 기준으로 짠 코스를
     * 상세가 종일(08시) 기준으로 되짚어, 같은 코스가 두 근거를 갖는다. 날짜를 옮기면 첫날 일정이 조용히 다시
     * 늘어난다 — 생성과 수정이 같은 규칙을 써야 한다는 #214 의 그 유형이다.
     *
     * <p>이 컬럼이 생기기 전 코스는 null 이고 {@link #startDayLeave()} 가 종일로 답한다 — 그때의 동작과 같다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "start_day_leave", length = 20)
    private StartDayLeave startDayLeave;

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
            int travelDays,
            Double originLat,
            Double originLng,
            StartDayLeave startDayLeave) {
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
        this.days = new ArrayList<>(days);
        this.travelDays = travelDays;
        this.travelDate = travelDate;
        this.originLat = originLat;
        this.originLng = originLng;
        this.startDayLeave = startDayLeave;
    }

    /**
     * 첫날에 쓴 연차. 이 컬럼이 생기기 전 코스는 null 이라 종일로 답한다.
     *
     * <p>지어내지 않고 그 시절 동작(가장 빠른 열차 = 아침 출발 전제)과 같은 값을 준다.
     */
    public StartDayLeave startDayLeave() {
        return startDayLeave == null ? StartDayLeave.DEFAULT : startDayLeave;
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
            int travelDays,
            StartDayLeave startDayLeave) {
        return new Course(
                null, regionId, density, transport, days, travelDate, travelDays, null, null, startDayLeave);
    }

    /**
     * 게스트 소유로 코스를 만든다(저장용). 게스트 ID 는 공백일 수 없고 길이 상한을 넘지 않는다(빈 값이면 모든 요청이 한 묶음을 공유).
     *
     * @param origin 출발지. 대중교통 열차 접근을 다시 계산하는 근거다(#187). 모르면 null
     */
    public static Course ownedBy(
            String guestId,
            Long regionId,
            Density density,
            TransportMode transport,
            List<DaySchedule> days,
            LocalDate travelDate,
            int travelDays,
            Coordinate origin,
            StartDayLeave startDayLeave) {
        Objects.requireNonNull(guestId, "게스트 ID는 필수입니다");
        if (guestId.isBlank()) {
            throw new IllegalArgumentException("게스트 ID는 비어 있을 수 없습니다");
        }
        if (guestId.length() > MAX_GUEST_ID_LENGTH) {
            throw new IllegalArgumentException("게스트 ID가 너무 깁니다: " + guestId.length());
        }
        return new Course(guestId, regionId, density, transport, days, travelDate, travelDays,
                origin == null ? null : origin.lat(), origin == null ? null : origin.lng(), startDayLeave);
    }

    /**
     * <b>소유자 없이</b> 영속하는 코스(#261) — 담지 않고 공유 링크만 만들 때.
     *
     * <p>공유 링크로 열려면 코스가 어딘가 있어야 하는데, 사용자는 이걸 "내 코스에 담았다" 고 여기지 않는다.
     * 그래서 <b>주인을 두지 않는다</b> — "내 코스" 조회는 전부 {@code guest_id} 로 좁히므로(목록·상세·삭제)
     * 주인이 없는 코스는 어느 질의에도 걸리지 않는다. 목록에서 빼려고 플래그를 더하고 질의마다 조건을
     * 붙이는 것보다, 애초에 소유 관계를 만들지 않는 편이 규칙이 하나로 끝난다.
     *
     * <p>그 대가로 <b>이 코스는 아무도 지울 수 없다</b>. 삭제도 소유자 범위로 도는 길뿐이기 때문이다.
     * 정리는 발급 시각({@code course_share.created_at})을 근거로 나중에 일괄로 한다.
     *
     * @param origin 출발지. 공개 조회에서 열차 접근을 다시 계산하는 근거다(#187). 모르면 null
     */
    public static Course sharedOnly(
            Long regionId,
            Density density,
            TransportMode transport,
            List<DaySchedule> days,
            LocalDate travelDate,
            int travelDays,
            Coordinate origin,
            StartDayLeave startDayLeave) {
        return new Course(null, regionId, density, transport, days, travelDate, travelDays,
                origin == null ? null : origin.lat(), origin == null ? null : origin.lng(), startDayLeave);
    }

    /**
     * 저장된 출발지 — 대중교통 열차 접근을 다시 계산할 근거.
     *
     * @return 출발지. 자차 코스이거나 이 필드가 생기기 전에 저장된 코스면 empty
     */
    public Optional<Coordinate> origin() {
        if (originLat == null || originLng == null) {
            return Optional.empty();
        }
        return Optional.of(new Coordinate(originLat, originLng));
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
     * 이 여행이 {@code today} 를 <b>포함</b>하는가 — 오늘 떠나거나, 오늘 여행 중이다(#189).
     *
     * <p>당일 운영 상태("오늘은 휴무일이에요")를 붙일지 가르는 데 쓴다. 그건 <b>지금 시각으로 내리는 판정</b>이라
     * 다음 주 코스에 붙이면 사용자가 여행일 상태로 읽는다 — 없는 것보다 나쁘다.
     *
     * <p>시작일만 보지 않는 이유: 3일 코스의 이튿날에 그 지역에 있는 사람에게 이 판정이 가장 쓸모 있는데,
     * 시작일 기준이면 바로 그때 사라진다.
     *
     * <p>여행 날짜가 없으면 언제인지 알 수 없으므로 거짓이다.
     */
    public boolean covers(LocalDate today) {
        return travelDate != null && !today.isBefore(travelDate) && !today.isAfter(travelEndDate());
    }

    /**
     * 전날 마지막 장소에서 {@code dayIndex} 번째 날 첫 장소까지의 직선거리(m) — 첫날은 null(#188).
     *
     * <p><b>여기가 비어 있었다.</b> 슬롯 사이 거리는 주면서 날짜가 바뀌는 구간만 없어, 숙소에서 다음날 첫
     * 장소가 40km 떨어져 있어도 화면에 아무 표시가 없었다. 1박2일·2박3일이면 사용자가 당연히 궁금해하는 구간이다.
     *
     * <p>좌표가 이미 슬롯에 있어 <b>외부 호출이 없다</b>. 슬롯 사이 거리와 같은 방식이다.
     */
    public Integer distanceFromPrevDayMeters(int dayIndex) {
        if (dayIndex <= 0 || dayIndex >= days.size()) {
            return null;
        }
        Optional<Slot> from = days.get(dayIndex - 1).lastSlot();
        Optional<Slot> to = days.get(dayIndex).firstSlot();
        if (from.isEmpty() || to.isEmpty()) {
            return null;
        }
        return straightLineMeters(from.get(), to.get());
    }

    /** 좌표가 없으면 지어내지 않는다 — 슬롯 좌표는 필수라 닿지 않는 게 정상이다. */
    private static Integer straightLineMeters(Slot from, Slot to) {
        if (from.getLat() == null || from.getLng() == null || to.getLat() == null || to.getLng() == null) {
            return null;
        }
        double km = new Coordinate(from.getLat(), from.getLng())
                .haversineKmTo(new Coordinate(to.getLat(), to.getLng()));
        return (int) Math.round(km * METERS_PER_KM);
    }

    /**
     * 하루 일정들 — <b>읽기 전용</b>으로 준다.
     *
     * <p>내부는 가변 리스트다({@link #trimFirstDayTo} 가 첫날을 걷어내야 하고, JPA 의 orphanRemoval 은
     * 컬렉션 <b>인스턴스를 바꾸지 말고</b> 내용을 고치라고 요구한다). 그렇다고 {@code @Getter} 가 그대로
     * 내주면 밖에서 애그리거트를 헤집을 수 있으므로 여기서 감싼다.
     */
    public List<DaySchedule> getDays() {
        return Collections.unmodifiableList(days);
    }

    /**
     * 첫날에서 <b>도착 전 시간대</b> 슬롯을 걷어낸다 — 날짜를 옮겨 도착이 늦어졌을 때(#214).
     *
     * <p>여행 날짜를 옮기면 열차 도착 시각은 새 날짜로 다시 조회하는데, 그 시각으로 내린 일정 판단은 저장된
     * 옛것이 남는다. 그래서 <b>도착 전 시간에 일정이 잡힌 코스</b>가 만들어진다 — 화면상 멀쩡한데 실제로는
     * 갈 수 없다. 조용히 틀리는 쪽이라 그대로 둘 수 없다.
     *
     * <p><b>걷어내기만 한다.</b> 반대 방향(첫날이 비었는데 이제 일찍 닿는다)은 채울 후보가 저장 코스에 없어
     * 외부를 다시 물어야 하고, 그러면 사용자가 고른 장소가 바뀐다. 그쪽은 호출자가 알려서 재생성을 권한다.
     *
     * <p><b>숙박은 남긴다.</b> 시간대 판정을 타지 않는 슬롯이다 — 밤늦게 닿아도 잘 곳은 필요하다
     * (생성 때의 {@code arrangeDay} 와 같은 규칙).
     *
     * <p>첫날이 통째로 비면 그 날을 없애고 남은 날의 표시 번호를 다시 붙인다 — "일차는 1부터 연속" 이
     * 이 애그리거트의 불변식이다. 달력 오프셋은 그대로 둔다(#159).
     *
     * @return 걷어낸 슬롯 수. 0 이면 바뀐 것이 없다
     */
    public int trimFirstDayTo(DayStart start) {
        Objects.requireNonNull(start, "첫날 가용 시간대가 필요합니다");
        DaySchedule first = days.getFirst();
        List<Slot> kept = first.getSlots().stream()
                .filter(slot -> slot.getKind() == SlotKind.STAY || start.allows(slot.getTimeOfDay()))
                .toList();
        int removed = first.slotCount() - kept.size();
        if (removed == 0) {
            return 0;
        }

        List<DaySchedule> rebuilt = new ArrayList<>();
        if (!kept.isEmpty()) {
            rebuilt.add(DaySchedule.of(first.getDayNumber(), first.getDayOffset(), renumber(kept)));
        }
        days.stream().skip(1).forEach(rebuilt::add);
        if (rebuilt.isEmpty()) {
            // 하루짜리 코스의 첫날이 통째로 빠지는 경우다. 코스는 하루 이상이 불변식이라 걷어내지 않는다 —
            // 갈 수 없는 일정이 남지만, 코스를 없애는 것은 날짜 수정이 할 일이 아니다.
            return 0;
        }
        // 첫날이 통째로 빠졌으면 표시 번호가 2 부터 시작한다 — 1 부터 연속으로 다시 붙인다.
        // 번호만 바꾼다: 슬롯은 이 애그리거트가 소유한 영속 엔티티라 새 DaySchedule 에 옮겨 담으면
        // orphanRemoval 이 옛 부모를 지우면서 슬롯까지 지운다.
        for (int i = 0; i < rebuilt.size(); i++) {
            rebuilt.get(i).renumberTo(i + 1);
        }
        days.clear();
        days.addAll(rebuilt);
        return removed;
    }

    /** 슬롯 순서를 1부터 다시 붙이고, 첫 슬롯의 이동시간을 0 으로 둔다(직전이 없어졌다). */
    private static List<Slot> renumber(List<Slot> slots) {
        List<Slot> renumbered = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            renumbered.add(Slot.of(i + 1, slot.getTimeOfDay(), slot.getKind(), slot.getPoiContentId(),
                    slot.getPoiContentTypeId(), slot.getTitle(), slot.getLat(), slot.getLng(),
                    i == 0 ? 0 : slot.getTravelMinutesFromPrev(),
                    new SlotDisplay(slot.getImageUrl(), slot.getAddress(), slot.getCatchphrase(), slot.getTel())));
        }
        return renumbered;
    }


    /**
     * 첫날이 <b>달력상 비어 있는가</b> — 생성 때 자정을 넘겨 닿아 통째로 이동이었다는 뜻이다.
     *
     * <p>일정이 있는 날만 담기고 달력 오프셋은 그대로 두므로(#159), 첫 일정이 오프셋 0 이 아니면 그 앞의
     * 하루가 비어 있는 것이다.
     */
    public boolean firstDayEmptyOnCalendar() {
        return days.getFirst().getDayOffset() > 0;
    }

    /**
     * "언제를 기준으로 판단하는가" — <b>여행일</b>이다. 날짜 없이 저장된 코스는 알 수 없어 {@code fallback} 으로
     * 물러선다.
     *
     * <p>혜택 매칭이 이 값을 쓴다. 정책에는 유효기간이 있어({@code Policy#isActiveOn}) 기준일이 곧 결과를
     * 가른다 — 오늘로 매칭하면 <b>여행 전에 끝나는 혜택이 보이고, 여행 기간에 시작하는 혜택은 안 보인다</b>.
     * 숙박세일페스타처럼 기간이 정해진 것이 7대 혜택의 절반이라 드문 일이 아니다(#213).
     *
     * <p>날짜가 없으면 정책을 아예 안 보여주는 선택지도 있었다. 그러지 않은 이유 — 그 코스도 언젠가는 가는
     * 여행이고, 오늘 유효한 혜택은 대개 가까운 미래에도 유효하다. 아무것도 안 보여주는 쪽이 더 틀린다.
     */
    public LocalDate travelDateOr(LocalDate fallback) {
        return travelDate != null ? travelDate : fallback;
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

    /**
     * 여행 날짜를 옮긴다(#170) — 편집 시트의 "여행날짜 수정".
     *
     * <p>기간({@link #travelDays})은 그대로다. 사용자가 고친 것은 언제 떠나는가지 며칠짜리 여행인가가 아니고,
     * 일정({@code days})의 오프셋도 시작일 기준이라 함께 따라간다.
     *
     * <p><b>차감량은 여기서 건드리지 않는다.</b> 평일 수·공휴일이 달라져 다시 계산해야 하는데 그건 외부 호출
     * (특일정보)이라 도메인이 할 일이 아니다. 호출자가 계산해 같은 트랜잭션에서 함께 반영한다.
     *
     * @param today 오늘(KST). 도메인이 시계를 직접 읽지 않게 받는다 — 그래야 테스트가 날짜를 고정할 수 있다
     */
    public void changeTravelDate(LocalDate newTravelDate, LocalDate today) {
        requireChangeableTo(newTravelDate, today);
        this.travelDate = newTravelDate;
    }

    /**
     * 이 날짜로 옮길 수 있는가 — <b>지난 날짜는 막는다.</b>
     *
     * <p>여행 날짜를 고치는 것은 앞으로의 계획을 손보는 일이다. 지난 날짜로 옮기면 그 코스는 즉시 "끝난 여행"
     * 이 돼(#116) 홈에서 "다녀오셨나요?" 를 묻는데, 사용자는 방금 계획을 고쳤을 뿐이다.
     *
     * <p><b>현재 날짜가 이미 지났어도 막지 않는다</b> — 판단 대상은 옮겨갈 날짜다. 날짜를 놓친 코스를 앞으로
     * 당겨오는 것이야말로 이 기능이 가장 필요한 경우다.
     *
     * <p>변경 경로 밖에서도 먼저 부를 수 있게 열어 둔다. 차감 재계산은 외부 호출을 타므로, 어차피 거절할
     * 요청으로 특일정보를 부르지 않게 서비스가 앞에서 한 번 거른다. 규칙은 여기 하나뿐이다.
     */
    public static void requireChangeableTo(LocalDate newTravelDate, LocalDate today) {
        Objects.requireNonNull(newTravelDate, "여행 시작일은 필수입니다");
        Objects.requireNonNull(today, "오늘 날짜는 필수입니다");
        if (newTravelDate.isBefore(today)) {
            throw ItineraryException.travelDateInPast();
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
