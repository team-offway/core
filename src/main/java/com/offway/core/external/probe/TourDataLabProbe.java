package com.offway.core.external.probe;

import org.springframework.stereotype.Component;

/**
 * 관광빅데이터(집중률·방문자 예측, data.go.kr 15101972).
 * 오퍼레이션 명세가 아직 미확정(활용가이드 v4.1 확인 필요)이라, 지금은 UNVERIFIED로 표시만 한다.
 */
@Component
class TourDataLabProbe implements ExternalApiProbe {

    @Override
    public ProbeResult probe() {
        return ProbeResult.unverified(
                "관광빅데이터(방문자·집중률)", "공공데이터포털",
                "활용신청 승인됨 · 클라이언트 연동 예정(지역별 방문자수·집중률·예측)");
    }
}
