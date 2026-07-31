package com.offway.core.leave.domain;

/**
 * 연차 현황 — 총 연차와 그동안 쓴 합. <b>남은 연차는 저장하지 않고 여기서 파생</b>한다.
 *
 * <p>남은 값을 따로 저장하면 사용 내역과 어긋날 수 있다(차감은 됐는데 남은 값 갱신이 실패하는 식). 내역을 정본으로 두면
 * 언제든 다시 계산해 맞출 수 있다.
 *
 * @param totalDays 총 연차
 * @param usedDays 사용 내역의 증감 합 (취소가 있으면 음수가 섞여 줄어든다)
 */
public record LeaveSummary(double totalDays, double usedDays) {

    /**
     * 남은 연차. <b>음수가 될 수 있다.</b> 남은 연차가 부족해도 서버는 막지 않기 때문이다(결정 #38) — 프론트가 경고하고
     * 사용자가 확인하면 진행한다. 여기서 0 으로 깎으면 "얼마나 초과했는지" 를 화면이 알 수 없다.
     */
    public double remainingDays() {
        return totalDays - usedDays;
    }
}
