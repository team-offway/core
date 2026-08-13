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
     * <p><b>수동 내역과 코스 차감이 이 값을 다르게 다룬다.</b> 뜻이 다르기 때문이다.
     *
     * <ul>
     *   <li><b>수동 내역</b> — 쓸 수 없다({@link #isValidUsage} 가 막는다). 순수한 증감 장부라 아무것도
     *       바꾸지 않는 행은 기록이 아니라 소음이다.
     *   <li><b>코스 차감</b> — 쓸 수 있다({@link #isValidCourseDeduction}). 그 행은 {@code courseId} 를 들고
     *       유니크 제약이 걸려 있어 차감량이자 <b>확정 표식</b>이라, 0 은 "확정했고 깎을 연차가 없었다" 는 뜻이다.
     *       재계산이 0 을 내놓아도 행을 남긴다(#212) — 지우면 날짜를 옮겼다는 이유로 확정이 조용히 풀린다.
     * </ul>
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
     * 사용 내역으로 쓸 수 있는 값인가 — <b>양수만</b>. 0 도 음수도 받지 않는다.
     *
     * <p>0 은 아무것도 바꾸지 않아 기록이 아니라 소음이다.
     *
     * <p><b>음수는 예전엔 받았다</b>(#265 에서 닫았다). 삭제 API 가 없던 시절 화면이 취소를 표현할 방법이
     * 음수 등록뿐이었기 때문이다. 그런데 그 등록은 아무 상한이 없어, 같은 취소를 두 번 보내면 사용 합이
     * 음수로 내려가고 <b>잔여가 총 연차를 넘었다</b> — 재시도 한 번에 없던 연차가 생긴 것이다. 취소는
     * 이제 {@code DELETE /me/usages/{id}} 로 한다. 상쇄 등록은 취소가 아니라 새 기록이라, 실수로 두 번
     * 보내면 장부가 그만큼 틀어진다.
     *
     * @see #isReversal(double)
     */
    public static boolean isValidUsage(double days) {
        return isValidUnit(days) && days > NONE && days <= MAX_TOTAL;
    }

    /**
     * 되돌리려고 넣는 음수 등록인가 — 받지 않는 값이지만 <b>0.5 단위 위반과는 사유가 다르다</b>(#265).
     *
     * <p>사유를 갈라야 화면이 "삭제로 취소하세요" 를 안내할 수 있다. 같은 400 으로 뭉뚱그리면 사용자는
     * 자기가 숫자를 잘못 넣은 줄 안다.
     */
    public static boolean isReversal(double days) {
        return days < NONE;
    }

    /**
     * 코스 확정으로 깎는 값인가 — <b>0 을 허용한다</b>(#212).
     *
     * <p>수동 내역과 규칙이 다른 이유는 그 행의 뜻이 다르기 때문이다. 수동 내역은 순수한 증감 장부라
     * 0 이 소음이지만, 코스 차감 행은 {@code courseId} 를 들고 유니크 제약이 걸려 있어 <b>차감량이자
     * 확정 표식</b>이다. 0 은 "이 코스는 확정했고, 깎을 연차가 없었다" 라는 뜻이라 기록할 값어치가 있다.
     *
     * <p>0 을 막았을 때 실제로 이런 일이 있었다 — 주말만으로 이뤄진 코스를 확정하면 차감이 0 이라
     * {@code LEAVE-010} 400 이 나갔다. 사용자는 정상적인 주말 여행을 확정한 것뿐이다.
     *
     * <p>음수는 받지 않는다. 코스 차감의 취소는 음수 누적이 아니라 <b>행 삭제</b>다(#113).
     */
    public static boolean isValidCourseDeduction(double days) {
        return isValidUnit(days) && days >= 0 && days <= MAX_TOTAL;
    }
}
