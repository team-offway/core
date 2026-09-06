package com.offway.core.inventory.infrastructure.probe;

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

    public static ProbeResult unverified(String name, String provider, String detail) {
        return new ProbeResult(name, provider, Status.UNVERIFIED, 0, detail, "");
    }
}
