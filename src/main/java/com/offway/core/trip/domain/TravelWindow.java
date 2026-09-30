package com.offway.core.trip.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 이 여행이 걸친 날짜 구간(#616).
 *
 * <h2>왜 값객체인가</h2>
 *
 * <p>예전에는 후보를 고를 때 <b>첫날 하나</b>만 넘겼다({@code collect(regionId, travelDate)}). 그래서
 * 2박3일인데 둘째·셋째 날에 열리는 축제가 통째로 사라졌고, 첫날만 하는 축제가 셋째 날 칸에 들어갔다.
 *
 * <p>"시작일 + 며칠" 을 날짜 두 개로 푸는 계산이 여러 자리에 흩어지면 한 군데만 틀려도 조용히 갈린다 —
 * 후보를 거르는 자리와 날에 배치하는 자리가 다른 답을 내면 "후보엔 있는데 아무 날에도 못 들어가는 축제"
 * 가 생긴다. 그 계산을 한 곳에 둔다.
 *
 * <p><b>조립이 아니라 계산이므로 빌더가 아니라 팩토리다</b>(CLAUDE.md). 끝일을 외부가 직접 세팅하게
 * 열면 시작일·일수와 어긋난 구간이 만들어진다.
 *
 * <p>여행일을 모르는 경로는 이 객체 대신 {@code null} 을 넘긴다 — 지금 {@code travelDate} 가 그렇게
 * 쓰이고 있고, "모른다" 를 값으로 감싸도 부르는 쪽이 guard clause 하나를 두는 것은 똑같다.
 */
public record TravelWindow(LocalDate start, LocalDate end) {

    /** 하루 여행의 최소 일수 — 0일 여행은 없다. */
    private static final int MIN_DAYS = 1;

    public TravelWindow {
        Objects.requireNonNull(start, "여행 시작일은 필수입니다");
        Objects.requireNonNull(end, "여행 종료일은 필수입니다");
        if (end.isBefore(start)) {
            throw new IllegalStateException("여행 종료일이 시작일보다 앞섭니다");
        }
    }

    /**
     * 시작일과 일수로 구간을 만든다.
     *
     * <p>{@code travelDays} 가 1 이면 시작일 하루다 — 끝일이 시작일과 같다. 여기서 {@code -1} 을
     * 빠뜨리면 구간이 하루씩 길어져, 마지막 날 다음날에 열리는 축제까지 후보로 올라온다.
     *
     * @param travelDays 며칠짜리 여행인가. {@link #MIN_DAYS} 아래는 하루로 본다
     */
    public static TravelWindow of(LocalDate start, int travelDays) {
        Objects.requireNonNull(start, "여행 시작일은 필수입니다");
        int days = Math.max(travelDays, MIN_DAYS);
        return new TravelWindow(start, start.plusDays(days - 1L));
    }

    /** 이 날에 여행 중인가 — 시작일·종료일 <b>당일을 포함</b>한다. */
    public boolean covers(LocalDate date) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * 이 구간과 저 구간이 <b>하루라도 겹치나</b>.
     *
     * <p>축제가 여행 중 하루라도 열리면 후보가 된다 — 여행 내내 열려야 하는 것이 아니다. 2박3일 중
     * 마지막 날 하루만 하는 축제도 갈 수 있는 축제다.
     *
     * @param otherStart 저쪽 시작일. {@code null} 이면 모르는 것이라 겹치는지 답할 수 없다
     * @param otherEnd 저쪽 종료일
     */
    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        if (otherStart == null || otherEnd == null) {
            return false;
        }
        return !otherStart.isAfter(end) && !otherEnd.isBefore(start);
    }

    /** 며칠짜리 여행인가 — 시작일과 종료일이 같으면 1 이다. */
    public int days() {
        return (int) (end.toEpochDay() - start.toEpochDay()) + 1;
    }

    /** 1일차부터 차례로 — 날에 배치할 때 그날의 날짜를 여기서 얻는다. */
    public Stream<LocalDate> dates() {
        return IntStream.range(0, days()).mapToObj(offset -> start.plusDays(offset));
    }

    /**
     * {@code dayNumber}(1부터) 의 날짜.
     *
     * @throws IllegalStateException 구간 밖의 날을 물으면 — 호출자가 여행 일수를 잘못 센 것이다
     */
    public LocalDate dateOf(int dayNumber) {
        if (dayNumber < MIN_DAYS || dayNumber > days()) {
            throw new IllegalStateException("여행 구간 밖의 날입니다: " + dayNumber);
        }
        return start.plusDays(dayNumber - 1L);
    }
}
