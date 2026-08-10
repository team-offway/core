package com.offway.core.leave.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 공휴일 적재가 <b>어느 달을 덮고, 그중 무엇을 다시 물을지</b>를 정한다(#193 3단계).
 *
 * <p>배치 서비스가 아니라 도메인에 둔 이유: 여기 담긴 것이 전부 판단(범위·재조회 여부)이고, 외부·DB 를 하나도
 * 건드리지 않는다. 스케줄러 밖으로 꺼내 두면 분기를 단위 테스트로 망라할 수 있다.
 */
public record HolidayRefreshWindow(YearMonth current, LocalDate today) {

    /** 과거로 덮을 개월 수 — 지난 날짜로 LNT 를 계산하는 경우를 위해 한 달만 둔다. */
    public static final int MONTHS_BACK = 1;

    /**
     * 미래로 덮을 개월 수.
     *
     * <p>샌드위치 조회 상한이 12개월이고({@code SandwichQuery}), 기간스타일 해석 창이 그 뒤로 며칠 더 나간다.
     * 이번 달 + {@value #MONTHS_AHEAD}개월이면 오늘 기준 요청은 전부 DB 로 답한다. 그보다 먼 날짜는 조회 쪽
     * 폴백이 받는다.
     */
    public static final int MONTHS_AHEAD = 13;

    public HolidayRefreshWindow {
        Objects.requireNonNull(current, "기준 연월이 필요합니다");
        Objects.requireNonNull(today, "기준 날짜가 필요합니다");
    }

    public static HolidayRefreshWindow of(LocalDate today) {
        return new HolidayRefreshWindow(YearMonth.from(today), today);
    }

    /** 덮을 달 — 지난달부터 {@value #MONTHS_AHEAD}개월 뒤까지, 오름차순. */
    public List<YearMonth> targetMonths() {
        List<YearMonth> months = new ArrayList<>();
        YearMonth month = current.minusMonths(MONTHS_BACK);
        YearMonth last = current.plusMonths(MONTHS_AHEAD);
        while (!month.isAfter(last)) {
            months.add(month);
            month = month.plusMonths(1);
        }
        return months;
    }

    /**
     * 그 달을 다시 물어야 하는가.
     *
     * <p><b>받은 적 없으면 받는다.</b>
     *
     * <p><b>지난 달은 한 번 받으면 끝이다</b> — 확정된 값이라 다시 물어야 할 이유가 없다.
     *
     * <p><b>이번 달 이후는 하루 한 번 다시 묻는다.</b> 아직 공표되지 않은 달이 빈 결과로 오는 함정이 있는데,
     * 한 번 적재하고 끝내면 그 빈 값이 영영 굳는다 — 공휴일이 평일로 세어져 소모 연차가 과다 계산된다.
     * 하루 한 번이면 공표·정정이 하루 안에 반영되고, <b>같은 날 재배포는 외부를 부르지 않는다</b>.
     *
     * @param stored 저장된 값. 받은 적 없으면 null
     */
    public boolean needsRefresh(YearMonth month, StoredHolidayMonth stored) {
        if (stored == null) {
            return true;
        }
        if (month.isBefore(current)) {
            return false;
        }
        return !stored.refreshedOn(today);
    }
}
