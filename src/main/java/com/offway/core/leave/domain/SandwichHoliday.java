package com.offway.core.leave.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 샌드위치 연휴 — 공휴일·주말 사이 평일에 연차를 끼워 최소 연차로 최대 휴식을 만드는 한 건의 기회.
 *
 * <p>이 값객체는 "연차를 언제 며칠 쓰면(=leaveDates) 어느 구간이 통째로 쉬는가(=window)"를 담고, 그 효율을 스스로 계산해 <b>황금
 * 연차인지 판단</b>한다(기능 F2). 달력 탐지({@link #detectWithin})도 이 도메인이 소유한다 — 공휴일 목록만 있으면 순수 계산이라
 * 외부 의존이 없다. 서비스는 공휴일 조회(특일정보)와 이 결과의 필터·정렬만 조율한다.
 *
 * @param windowStart 연속 휴식 구간의 시작일 (쉬는 날 — 주말/공휴일)
 * @param windowEnd 연속 휴식 구간의 종료일 (쉬는 날 — 주말/공휴일)
 * @param leaveDates 구간을 잇기 위해 연차로 소모하는 평일들 (징검다리). 오름차순·중복 없음
 */
public record SandwichHoliday(LocalDate windowStart, LocalDate windowEnd, List<LocalDate> leaveDates) {

    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    /**
     * 한 기회에서 잇는 징검다리 평일의 최대 개수.
     *
     * <p>2일까지만 잇는다. 3일 이상 떨어진 휴식 블록을 억지로 연결하면 그건 "샌드위치"가 아니라 그냥 평일 연차 소진이라 효율이 급락한다.
     */
    private static final int MAX_BRIDGE_DAYS = 2;

    /**
     * 황금 연차 판정 임계치 — 연차 1일당 최소 휴식 일수.
     *
     * <p>효율(총 휴식일 ÷ 소모 연차)이 이 값 이상이면 추천할 가치가 있는 "황금 연차"로 본다.
     */
    private static final double GOLDEN_MIN_EFFICIENCY = 2.0;

    /**
     * 불변식 검증 + 방어적 복사. leaveDates 는 상위(탐지 로직)가 항상 유효하게 넘긴다 — 여기 닿는 위반은 버그다.
     */
    public SandwichHoliday {
        Objects.requireNonNull(windowStart, "windowStart 는 null 일 수 없습니다.");
        Objects.requireNonNull(windowEnd, "windowEnd 는 null 일 수 없습니다.");
        Objects.requireNonNull(leaveDates, "leaveDates 는 null 일 수 없습니다.");
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException(
                    "종료일이 시작일보다 앞섭니다: start=%s end=%s".formatted(windowStart, windowEnd));
        }
        if (leaveDates.isEmpty()) {
            throw new IllegalArgumentException("연차를 하루도 안 쓰면 샌드위치 연휴가 아닙니다.");
        }

        List<LocalDate> sorted = leaveDates.stream().sorted().distinct().toList();
        if (sorted.size() != leaveDates.size()) {
            throw new IllegalArgumentException("leaveDates 에 중복이 있습니다: %s".formatted(leaveDates));
        }
        for (LocalDate leave : sorted) {
            if (leave.isBefore(windowStart) || leave.isAfter(windowEnd)) {
                throw new IllegalArgumentException("연차일이 휴식 구간 밖입니다: %s".formatted(leave));
            }
            if (WEEKEND.contains(leave.getDayOfWeek())) {
                throw new IllegalArgumentException("주말은 연차로 소모하지 않습니다: %s".formatted(leave));
            }
        }
        leaveDates = List.copyOf(sorted);
    }

    /**
     * 조회 구간 [{@code from}, {@code to}] 안에서 샌드위치 연휴 기회를 모두 찾는다.
     *
     * <p>주말·공휴일을 쉬는 날로 보고 연속 블록으로 묶은 뒤, {@link #MAX_BRIDGE_DAYS} 이하로 떨어진 이웃 블록을 평일 연차로 이어
     * 하나의 휴식 구간을 만든다. 각 구간이 한 건의 {@code SandwichHoliday} 다. 효율이 높은 순으로 정렬해 돌려준다.
     *
     * @param from 조회 시작일 (포함)
     * @param to 조회 종료일 (포함)
     * @param holidays 구간에 걸치는 공휴일
     * @return 탐지된 샌드위치 연휴 목록 (효율 내림차순, 동률이면 시작일 오름차순)
     */
    public static List<SandwichHoliday> detectWithin(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        Objects.requireNonNull(from, "from 은 null 일 수 없습니다.");
        Objects.requireNonNull(to, "to 는 null 일 수 없습니다.");
        Objects.requireNonNull(holidays, "holidays 는 null 일 수 없습니다.");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("조회 종료일이 시작일보다 앞섭니다: from=%s to=%s".formatted(from, to));
        }

        List<int[]> offBlocks = restBlocks(from, to, holidays);
        List<SandwichHoliday> found = new ArrayList<>();

        for (int i = 0; i < offBlocks.size(); i++) {
            // 앞 블록에서 이미 이어졌으면(이 블록이 그 체인의 일부면) 새로 시작하지 않는다 — 최대 구간만 남긴다.
            if (i > 0 && bridgeSize(offBlocks.get(i - 1), offBlocks.get(i)) <= MAX_BRIDGE_DAYS) {
                continue;
            }

            LocalDate windowStart = from.plusDays(offBlocks.get(i)[0]);
            LocalDate windowEnd = from.plusDays(offBlocks.get(i)[1]);
            List<LocalDate> leaveDates = new ArrayList<>();

            int j = i;
            while (j + 1 < offBlocks.size() && bridgeSize(offBlocks.get(j), offBlocks.get(j + 1)) <= MAX_BRIDGE_DAYS) {
                for (int day = offBlocks.get(j)[1] + 1; day < offBlocks.get(j + 1)[0]; day++) {
                    leaveDates.add(from.plusDays(day));
                }
                windowEnd = from.plusDays(offBlocks.get(j + 1)[1]);
                j++;
            }

            if (!leaveDates.isEmpty()) {
                found.add(new SandwichHoliday(windowStart, windowEnd, leaveDates));
            }
        }

        return found.stream()
                .sorted((a, b) -> {
                    int byEfficiency = Double.compare(b.efficiency(), a.efficiency());
                    return byEfficiency != 0 ? byEfficiency : a.windowStart.compareTo(b.windowStart);
                })
                .toList();
    }

    /** 소모하는 연차 일수 (= 징검다리 평일 수). */
    public int leaveDays() {
        return leaveDates.size();
    }

    /** 연속 휴식 일수 (구간의 양 끝 포함). */
    public int totalRestDays() {
        return (int) (windowEnd.toEpochDay() - windowStart.toEpochDay() + 1);
    }

    /** 효율 — 연차 1일이 만들어내는 휴식 일수. */
    public double efficiency() {
        return (double) totalRestDays() / leaveDays();
    }

    /** 추천할 가치가 있는 황금 연차인가. */
    public boolean isGolden() {
        return efficiency() >= GOLDEN_MIN_EFFICIENCY;
    }

    /**
     * 구간 안 "쉬는 날(주말·공휴일)"의 연속 블록을 {@code from} 기준 오프셋 [시작, 끝] 쌍으로 반환한다.
     */
    private static List<int[]> restBlocks(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        List<int[]> blocks = new ArrayList<>();
        int offset = 0;
        int[] current = null;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1), offset++) {
            if (isRestDay(day, holidays)) {
                if (current == null) {
                    current = new int[] {offset, offset};
                } else {
                    current[1] = offset;
                }
            } else if (current != null) {
                blocks.add(current);
                current = null;
            }
        }
        if (current != null) {
            blocks.add(current);
        }
        return blocks;
    }

    /** 두 휴식 블록 사이에 낀 평일 수 (이어 붙이려면 연차로 소모해야 하는 날 수). */
    private static int bridgeSize(int[] left, int[] right) {
        return right[0] - left[1] - 1;
    }

    private static boolean isRestDay(LocalDate day, Set<LocalDate> holidays) {
        return WEEKEND.contains(day.getDayOfWeek()) || holidays.contains(day);
    }
}
