package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.external.ExternalHttpOutcome;

public record ProbeResult(
        String name, String provider, Status status, int httpStatus, String detail, String sample) {

    public enum Status {
        OK, FAIL, SKIPPED_NO_KEY, UNVERIFIED
    }

    public static ProbeResult ok(String name, String provider, int httpStatus, String sample) {
        return new ProbeResult(name, provider, Status.OK, httpStatus, "정상 응답", sample);
    }

    public static ProbeResult fail(String name, String provider, int httpStatus, String detail, String sample) {
        return new ProbeResult(name, provider, Status.FAIL, httpStatus, detail, sample);
    }

    public static ProbeResult skipped(String name, String provider) {
        return new ProbeResult(name, provider, Status.SKIPPED_NO_KEY, 0, "키 없음 — 건너뜀", "");
    }

    /**
     * 지금 못 쓰는 상태인가 — 어드민 표의 색과 프로브 재확인(#474)이 같은 기준을 쓰게 한다.
     *
     * <p>키가 없어 건너뛴 것은 실패가 아니다. 로컬·미설정 환경의 정상 상태이고, 그걸 실패로 세면
     * 키를 안 꽂은 개발 환경이 늘 장애로 보인다.
     */
    public boolean unusable() {
        return status == Status.FAIL;
    }

    /**
     * 이 결과로 시스템 상태를 판정해도 되나(#479).
     *
     * <p>키가 없어 건너뛴 것과 아직 못 재본 것은 <b>외부에 대해 아무것도 말해주지 않는다.</b> 그걸
     * 성공으로 세면 죽은 API 가 살아 있는 것으로 보이고, 실패로 세면 키를 안 꽂은 환경이 늘 장애가 된다.
     */
    public boolean observed() {
        return status == Status.OK || status == Status.FAIL;
    }

    /**
     * 그 자리에서 다시 물어볼 값어치가 있나(#474).
     *
     * <p><b>다시 묻는 것과 알리는 것은 다른 질문이다(#489).</b> 401·403 처럼 막힌 것은 몇 번을 더 물어도
     * 같은 답이 오므로 확인 호출을 아낀다 — 그렇다고 조용히 넘기는 것은 아니다. 알림은
     * {@link ExternalHttpOutcome#isFailure()} 가 따로 판단한다.
     *
     * <p>판정 기준은 {@link ExternalHttpOutcome} 한 곳이 소유한다. 전에는 어드민 표와 알림이 각자
     * 4xx 를 판단해 서로 다른 말을 할 수 있었다.
     *
     * <p>표시({@link #unusable()})와는 다른 질문이다. 키가 잘못돼 못 쓰는 것도 <b>못 쓰는 상태</b>라
     * 어드민에는 실패로 보여야 하지만, 다시 물어볼 이유는 없다.
     */
    public boolean worthConfirming() {
        return unusable() && ExternalHttpOutcome.of(httpStatus).worthRetrying();
    }


    public static ProbeResult unverified(String name, String provider, String detail) {
        return new ProbeResult(name, provider, Status.UNVERIFIED, 0, detail, "");
    }
}
