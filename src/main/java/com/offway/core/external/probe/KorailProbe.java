package com.offway.core.external.probe;

import org.springframework.stereotype.Component;

/**
 * 코레일 열차운행정보(data.go.kr 15125762).
 * 오퍼레이션 URL 미확정이라 지금은 UNVERIFIED. KTX 시간표는 TAGO 열차정보로도 대체 가능.
 */
@Component
class KorailProbe implements ExternalApiProbe {

    @Override
    public ProbeResult probe() {
        return ProbeResult.unverified(
                "코레일 열차운행정보", "공공데이터포털",
                "활용신청 승인됨 · 클라이언트 연동 예정(여객열차 운행계획/운행정보)");
    }
}
