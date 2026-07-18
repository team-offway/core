package com.offway.core.external.probe;

final class ProbeSupport {

    private static final int SAMPLE_MAX = 300;

    private ProbeSupport() {
    }

    /** 응답 본문을 로그·화면용으로 한 줄 축약한다. */
    static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.strip().replaceAll("\\s+", " ");
        return oneLine.length() > SAMPLE_MAX ? oneLine.substring(0, SAMPLE_MAX) + "…" : oneLine;
    }
}
