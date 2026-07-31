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
     * 총 연차 상한. 법정 연차가 25일 안팎이고 이월을 넉넉히 감안해도 이 값을 넘을 이유가 없다. 상한이 없으면 오타
     * 하나로 비현실적인 값이 저장돼 이후 화면이 이상해진다.
     */
    public static final double MAX_TOTAL = 365.0;

    private LeaveDays() {
    }

    /** 0.5 의 배수인가. 부동소수 비교라 2배 해서 정수인지로 본다. */
    public static boolean isValidUnit(double days) {
        double doubled = days * 2;
        return Double.isFinite(days) && doubled == Math.rint(doubled);
    }

    /** 총 연차로 쓸 수 있는 값인가 — 음수 불가, 상한 이하, 0.5 단위. */
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
