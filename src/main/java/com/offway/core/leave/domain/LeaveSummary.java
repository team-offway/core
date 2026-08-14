package com.offway.core.leave.domain;

/**
 * 연차 현황 — 총 연차와 그동안 쓴 합. <b>남은 연차는 저장하지 않고 여기서 파생</b>한다.
 *
 * <p>남은 값을 따로 저장하면 사용 내역과 어긋날 수 있다(차감은 됐는데 남은 값 갱신이 실패하는 식). 내역을 정본으로 두면
 * 언제든 다시 계산해 맞출 수 있다.
 *
 * <p><b>이 값객체가 "잔여는 총 연차를 넘지 않는다" 를 보장한다</b>(#265). 예전엔 원장 합을 그대로 빼서
 * 내려줬는데, 합이 음수로 내려가면 잔여가 총보다 커졌다 — 총 15일에 2일 쓴 사람이 취소를 두 번 보내면
 * 잔여가 17이 됐고, 그건 재시도 한 번으로 없던 연차가 생겼다는 뜻이다. 사용 합을 만들어내는 경로가
 * 지금은 하나뿐이지만(수동 등록), 여기서 보장하면 <b>어느 경로로 들어오든</b> 잔여가 총을 못 넘는다.
 *
 * @param totalDays 총 연차
 * @param usedDays 쓴 연차 — <b>0 아래로 내려가지 않는다</b>. "쓴 연차가 -2일" 은 뜻이 없다
 * @param ledgerDays 자른 적 없는 원장 합. {@link #usedDays} 와 다르면 데이터가 이미 어긋나 있다는 신호다
 */
public record LeaveSummary(double totalDays, double usedDays, double ledgerDays) {

    /** 아무것도 쓰지 않은 상태. 사용 합의 하한이기도 하다. */
    private static final double NOTHING_USED = 0;

    /**
     * 불변식 — {@link #of} 가 이미 자르므로 여기 닿는 위반은 버그다(500). 그래도 두는 이유는 누가 만들든
     * 스스로 유효함을 보장하는 최후의 보루이기 때문이다.
     */
    public LeaveSummary {
        if (usedDays < NOTHING_USED) {
            throw new IllegalArgumentException("쓴 연차는 음수일 수 없습니다: " + usedDays);
        }
    }

    /**
     * 원장 합에서 현황을 계산한다 — <b>조립이 아니라 계산이라 팩토리다.</b>
     *
     * <p>음수 원장 합은 0 으로 본다. 그렇게 만든 데이터가 이미 남아 있을 수 있어(삭제 API 가 없던 시절의
     * 상쇄 등록) 거절하면 조회 자체가 500 이 되는데, 사용자는 화면을 못 여는 것으로 그 사실을 알게 된다.
     * 자른 사실은 {@link #isLedgerNegative()} 로 드러내 호출자가 로그를 남긴다 — 조용히 넘어가지 않는다.
     *
     * @param ledgerDays 사용 내역의 증감 합
     */
    public static LeaveSummary of(double totalDays, double ledgerDays) {
        return new LeaveSummary(totalDays, Math.max(NOTHING_USED, ledgerDays), ledgerDays);
    }

    /**
     * 남은 연차. <b>총 연차를 넘지 않고</b>(#265), <b>음수는 될 수 있다.</b>
     *
     * <p>음수를 허용하는 이유는 남은 연차가 부족해도 서버가 막지 않기 때문이다(결정 #38) — 프론트가 경고하고
     * 사용자가 확인하면 진행한다. 여기서 0 으로 깎으면 "얼마나 초과했는지" 를 화면이 알 수 없다.
     *
     * <p>위쪽을 막고 아래쪽을 여는 것이 비대칭으로 보이지만, 두 방향은 뜻이 다르다. 초과 사용은 사용자가
     * 확인하고 만든 <b>사실</b>이고, 총을 넘는 잔여는 <b>있을 수 없는 값</b>이다.
     */
    public double remainingDays() {
        return totalDays - usedDays;
    }

    /**
     * 원장 합이 음수라 잘렸는가 — 상쇄 등록이 남긴 데이터의 흔적이다.
     *
     * <p>참이면 그 소유자의 내역에 음수 행이 섞여 있다. 사용자는 이제 그 행을 삭제해 정리할 수 있다.
     */
    public boolean isLedgerNegative() {
        return ledgerDays < NOTHING_USED;
    }
}
