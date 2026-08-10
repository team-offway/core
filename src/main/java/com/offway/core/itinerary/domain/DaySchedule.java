package com.offway.core.itinerary.domain;

import com.offway.core.transport.domain.Coordinate;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 코스의 하루(Day 탭 하나) — 그 날의 슬롯을 순서대로 담는다. {@link Course} 애그리거트 내부라 {@code @OneToMany} 로 슬롯을
 * 함께 로드/저장한다(생명주기 공유). DB FK 는 두지 않는다(persistence-convention).
 */
@Entity
@Table(name = "day_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DaySchedule {

    /** km 로 계산한 대권거리를 화면 단위(m)로 바꾼다. */
    private static final int METERS_PER_KM = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 화면에 보이는 며칠째(1부터 연속). Day 탭의 번호다. */
    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    /**
     * 여행 시작일로부터 며칠 뒤인지(0부터). 날짜·날씨는 이 값으로 계산한다.
     *
     * <p><b>표시 번호와 나눠 두는 이유</b> — 일정이 하나도 없는 날은 코스에서 빠진다(늦게 도착해 아무것도
     * 못 하는 날). 그러면 둘째 날이 {@code day 1} 이 되는데, 날짜까지 표시 번호로 계산하면 하루가
     * 앞당겨진다. 화면의 탭은 1·2·3 으로 이어지되 날짜는 달력을 따라야 한다(#159).
     */
    @Column(name = "day_offset", nullable = false)
    private int dayOffset;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(
            name = "day_schedule_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @OrderBy("orderInDay")
    private List<Slot> slots;

    private DaySchedule(int dayNumber, int dayOffset, List<Slot> slots) {
        if (dayNumber < 1) {
            throw new IllegalArgumentException("일차는 1 이상이어야 합니다: " + dayNumber);
        }
        if (dayOffset < 0) {
            throw new IllegalArgumentException("여행 시작일로부터의 일수는 0 이상이어야 합니다: " + dayOffset);
        }
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("하루에는 슬롯이 최소 하나 있어야 합니다");
        }
        requireSequentialOrder(slots);
        this.dayNumber = dayNumber;
        this.dayOffset = dayOffset;
        this.slots = List.copyOf(slots);
    }

    /**
     * 하루 일정을 만든다. 슬롯 순서가 1부터 빠짐없이 이어지는지 검증한다.
     *
     * @param dayNumber 화면에 보이는 며칠째(1부터 연속)
     * @param dayOffset 여행 시작일로부터 며칠 뒤인지(0부터) — 날짜·날씨 계산의 근거
     */
    public static DaySchedule of(int dayNumber, int dayOffset, List<Slot> slots) {
        return new DaySchedule(dayNumber, dayOffset, slots);
    }

    /** 표시 번호와 달력 위치가 같은 경우(첫날부터 일정이 있는 흔한 코스). */
    public static DaySchedule of(int dayNumber, List<Slot> slots) {
        return new DaySchedule(dayNumber, dayNumber - 1, slots);
    }

    /** 이 날의 슬롯 수. */
    /**
     * 앞 슬롯과의 직선거리(m) — 화면이 장소 사이에 {@code 8.3km}·{@code 154m} 를 그리는 재료다(#141).
     *
     * <p>좌표가 이미 있어 <b>추가 외부 호출이 없다.</b> 이동시간(TMAP 실측)과는 다른 값이다 — 그쪽은 도로를
     * 따라가고 이쪽은 직선이라 값이 어긋나지만, 둘 다 사실이라 함께 보여줘도 된다.
     *
     * @param index 슬롯 위치(0부터)
     * @return 앞 슬롯과의 거리(m). <b>첫 슬롯은 null</b> — 이동 전이라 0 이 아니라 없음이다
     */
    public Integer distanceFromPrevMeters(int index) {
        if (index <= 0 || index >= slots.size()) {
            return null;
        }
        Slot prev = slots.get(index - 1);
        Slot current = slots.get(index);
        if (prev.getLat() == null || prev.getLng() == null
                || current.getLat() == null || current.getLng() == null) {
            return null; // 좌표는 필수라 닿지 않는 게 정상이지만, 닿으면 지어내지 않는다
        }
        double km = new Coordinate(prev.getLat(), prev.getLng())
                .haversineKmTo(new Coordinate(current.getLat(), current.getLng()));
        return (int) Math.round(km * METERS_PER_KM);
    }

    /**
     * 표시 번호만 바꾼다 — 앞의 날이 통째로 빠져 번호를 다시 붙일 때(#214).
     *
     * <p><b>새 인스턴스를 만들지 않는다.</b> 슬롯은 이 애그리거트가 소유한 영속 엔티티라, 같은 것을 새
     * {@code DaySchedule} 에 옮겨 담으면 orphanRemoval 이 옛 부모를 지우면서 슬롯까지 지운다.
     */
    void renumberTo(int dayNumber) {
        if (dayNumber < 1) {
            throw new IllegalArgumentException("일차는 1부터입니다: " + dayNumber);
        }
        this.dayNumber = dayNumber;
    }

    public int slotCount() {
        return slots.size();
    }

    private static void requireSequentialOrder(List<Slot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            int expected = i + 1;
            if (slots.get(i).getOrderInDay() != expected) {
                throw new IllegalArgumentException(
                        "슬롯 순서가 1부터 연속이어야 합니다: " + expected + " 위치에 " + slots.get(i).getOrderInDay());
            }
        }
    }
}
