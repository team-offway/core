package com.offway.core.leave.domain;

import java.time.LocalTime;

/**
 * 여행 첫날에 쓴 연차 — <b>얼마를 쓰는지와 몇 시에 떠나는지를 함께 소유한다</b>(#138).
 *
 * <p><b>왜 불리언이 아닌가.</b> 예전에는 {@code boolean halfDayStart} 였다. 값이 둘일 때는 됐지만 반반차가
 * 들어오면서 셋이 됐고, 그때부터 호출부마다 "true 면 반차, false 면 종일, 그럼 반반차는?" 을 다시 판단해야 한다.
 * 셋을 두 개의 불리언으로 표현하면 <b>있을 수 없는 조합</b>(반차이면서 반반차)이 타입에 남는다.
 *
 * <p><b>왜 시각까지 여기 두는가.</b> 출발 시각은 이 선택에서 <b>도출되는</b> 값이다 — 반차를 냈으니 오후에
 * 떠난다. 두 값을 따로 두면 "반차인데 08시 출발" 같은 어긋난 조합을 누군가 만든다. 한 상수가 둘을 함께 들면
 * 그 조합이 애초에 표현되지 않는다.
 *
 * <p><b>출발 시각이 무엇을 바꾸는가.</b> 첫날은 이동에 먹힌다. 15시에 떠나면 오전·점심 시간대가 이미 지났는데,
 * 그 사실을 모르고 일정을 넣으면 코스가 지킬 수 없는 약속이 된다({@code DayStart} 참고). 이 서비스는 LNT 가
 * 핵심 개념이라 그 과대계산이 특히 아프다 — 연차를 그만큼 잘못 쓰게 만든다.
 *
 * <p><b>모르는 값은 우리가 거절하지 않는다.</b> 요청 dto 가 이 타입으로 받으므로 Jackson 이 파싱하고, 오타는
 * 본문을 읽을 수 없다는 프레임워크 400({@code COMMON-400})이 된다. 여기에 {@code from(String)} 파서와 전용
 * 에러코드를 두려 했는데 <b>아무도 부르지 않아 죽은 코드였다</b> — 에러코드 번호는 append-only 라 한 번 쓰면
 * 영구히 남으므로 넣지 않았다.
 *
 * <p><b>기준 시각은 상수로 박는다.</b> 실측이 아니라 팀이 정한 값이라 근거가 코드 밖에 있다. 바꿀 때 한 자리만
 * 고치게 두고, 사용자가 시각을 직접 고르고 싶다는 요구가 오면 그때 파라미터를 연다.
 */
public enum StartDayLeave {

    /** 종일 연차 — 하루를 다 썼으니 아침부터 움직인다. */
    FULL_DAY("연차", 1.0, LocalTime.of(8, 0)),

    /** 반차 — 오전 근무를 마치고 점심 무렵 떠난다. */
    HALF_DAY("반차", 0.5, LocalTime.of(12, 0)),

    /** 반반차 — 근무를 거의 마치고 늦은 오후에 떠난다. */
    QUARTER_DAY("반반차", 0.25, LocalTime.of(15, 0));

    /** 값이 없을 때의 기준. 안 보내던 클라이언트가 지금과 같은 결과를 받아야 한다. */
    public static final StartDayLeave DEFAULT = FULL_DAY;

    private final String label;
    private final double consumedLeave;
    private final LocalTime departureTime;

    StartDayLeave(String label, double consumedLeave, LocalTime departureTime) {
        this.label = label;
        this.consumedLeave = consumedLeave;
        this.departureTime = departureTime;
    }

    /** 예전 계약({@code halfDayStart} 불리언)에서 옮겨온다. 앱이 갈아타는 동안 둘을 함께 받는다. */
    public static StartDayLeave fromHalfDayFlag(Boolean halfDayStart) {
        return Boolean.TRUE.equals(halfDayStart) ? HALF_DAY : FULL_DAY;
    }

    /** 사용자 대면 표기 — "반차" 처럼 화면에 그대로 쓸 수 있는 문구. */
    public String label() {
        return label;
    }

    /** 이 선택이 첫날에 소모하는 연차. 첫날이 주말·공휴일이면 애초에 소모가 없어 이 값을 쓰지 않는다. */
    public double consumedLeave() {
        return consumedLeave;
    }

    /** 집을 나서는 시각. 첫날 도착 시각의 기준이다. */
    public LocalTime departureTime() {
        return departureTime;
    }

    /** 하루를 통째로 쓰는가 — 첫날 연차를 깎지 않는 경로가 이 값으로 갈린다. */
    public boolean isFullDay() {
        return this == FULL_DAY;
    }
}
