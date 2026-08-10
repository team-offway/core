package com.offway.core.leave.domain;

/**
 * 연차 일수 값 규칙. 총 연차·사용 내역이 같은 규칙을 쓰도록 한 곳에 모은다.
 *
 * <p>연차는 <b>0.5 단위</b>다 — 반차가 0.5 이고(결정 #38), 스테퍼에 1.5 같은 값을 직접 넣는다. 0.3 같은 값이 들어오면
 * 이후 계산이 조용히 이상해지므로 경계에서 막는다.
 */
public final class LeaveDays {

    /** 최소 단위 — 반차. */
    public static final double UNIT = 0.5;

    /**
     * 깎을 연차가 없음 — 구간이 주말·공휴일뿐일 때 계산 결과가 이 값이다.
     *
     * <p>사용 내역으로는 쓸 수 없다({@link #isValidUsage} 가 막는다). 재계산이 이 값을 내놓으면 내역을
     * 갱신하는 게 아니라 <b>지운다</b>(#170).
     */
    public static final double NONE = 0;

    /**
     * 총 연차 상한 — <b>화면이 사용자에게 약속한 값과 같다</b>(#142).
     *
     * <p>온보딩 화면이 "최대 99일까지 입력할 수 있어요" 라고 안내한다. 서버가 그보다 넉넉하면 화면을 거치지
     * 않은 요청만 다른 규칙을 따르게 되고, 그건 계약이 두 개인 것과 같다.
     *
     * <p>예전 값은 365 였는데 "법정 연차 25일 안팎에 이월을 감안해도 넘을 이유가 없다" 는 설명과 어긋났다 —
     * 365 는 1년 전체라 넉넉함이 아니라 사실상 상한이 없는 것에 가깝다.
     */
    public static final double MAX_TOTAL = 99.0;

    private LeaveDays() {
    }

    /** 0.5 의 배수인가. 부동소수 비교라 2배 해서 정수인지로 본다. */
    public static boolean isValidUnit(double days) {
        double doubled = days * 2;
        return Double.isFinite(days) && doubled == Math.rint(doubled);
    }

    /** 총 연차로 쓸 수 있는 값인가 — 음수 불가, 상한 이하, 0.5 단위. <b>0 과 상한은 허용</b>한다. */
    public static boolean isValidTotal(double days) {
        return isValidUnit(days) && days >= 0 && days <= MAX_TOTAL;
    }

    /**
     * 사용 내역의 증감으로 쓸 수 있는 값인가.
     *
     * <p>음수를 허용한다 — 코스를 취소하면 쓴 연차를 되돌려야 하고, 그걸 <b>내역을 지워서</b> 하면 "언제 무엇이
     * 취소됐는지" 가 사라진다. 다만 <b>0 은 막는다</b>: 아무것도 바꾸지 않는 내역은 기록이 아니라 소음이다.
     */
    public static boolean isValidUsage(double days) {
        return isValidUnit(days) && days != 0 && Math.abs(days) <= MAX_TOTAL;
    }
}
