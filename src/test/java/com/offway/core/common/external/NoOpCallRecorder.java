package com.offway.core.common.external;

/**
 * 사용량을 세지 않는 기록기 — 클라이언트를 직접 만드는 단위 테스트용.
 *
 * <p>단위 테스트의 관심사는 "외부 응답을 어떻게 해석하는가" 지 "몇 번 불렀는가" 가 아니다. 진짜 기록기를 쓰면
 * DB 가 필요해져 단위 테스트가 통합 테스트가 된다.
 *
 * <p>세는 동작 자체는 {@code ExternalApiQuotaIntegrationTest} 가 실제 저장소로 검증한다.
 */
public final class NoOpCallRecorder extends ExternalApiCallRecorder {

    public NoOpCallRecorder() {
        super(null, null);
    }

    @Override
    public void record(ExternalApi api) {
        // 아무것도 하지 않는다.
    }
}
