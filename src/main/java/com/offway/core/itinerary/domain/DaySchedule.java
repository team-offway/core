package com.offway.core.itinerary.domain;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 며칠째(1부터). */
    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(
            name = "day_schedule_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @OrderBy("orderInDay")
    private List<Slot> slots;

    private DaySchedule(int dayNumber, List<Slot> slots) {
        if (dayNumber < 1) {
            throw new IllegalArgumentException("일차는 1 이상이어야 합니다: " + dayNumber);
        }
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("하루에는 슬롯이 최소 하나 있어야 합니다");
        }
        requireSequentialOrder(slots);
        this.dayNumber = dayNumber;
        this.slots = List.copyOf(slots);
    }

    /** 하루 일정을 만든다. 슬롯 순서가 1부터 빠짐없이 이어지는지 검증한다. */
    public static DaySchedule of(int dayNumber, List<Slot> slots) {
        return new DaySchedule(dayNumber, slots);
    }

    /** 이 날의 슬롯 수. */
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
