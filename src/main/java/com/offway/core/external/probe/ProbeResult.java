package com.offway.core.external.probe;

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

    public static ProbeResult unverified(String name, String provider, String detail) {
        return new ProbeResult(name, provider, Status.UNVERIFIED, 0, detail, "");
    }
}
