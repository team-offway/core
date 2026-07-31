package com.offway.core.itinerary.domain;

/**
 * 지난 여행을 실제로 다녀왔는가 — 홈 모달 "다녀오셨나요?" 의 답(#116).
 *
 * <p>닫힌 두 경우라 enum 이고, <b>연차를 깎는지</b>를 각 상수가 스스로 안다. 서비스에
 * {@code if (outcome == VISITED)} 분기를 쌓지 않는다.
 */
public enum VisitOutcome {

    /** 다녀왔다 — 계획했던 연차를 이제 깎는다. */
    VISITED(true),

    /**
     * 안 갔다 — 깎지 않는다.
     *
     * <p>안 간 것도 <b>기록해야 한다</b>. 기록하지 않으면 "아직 안 물어본 것" 과 구분되지 않아 홈을 열 때마다 다시 뜬다.
     */
    NOT_VISITED(false);

    private final boolean deductsLeave;

    VisitOutcome(boolean deductsLeave) {
        this.deductsLeave = deductsLeave;
    }

    public boolean deductsLeave() {
        return deductsLeave;
    }
}
