package com.offway.core.weather.infrastructure.airkorea;

import com.offway.core.weather.domain.AirQuality;
import java.util.Optional;

/**
 * 에어코리아(한국환경공단) 대기오염정보 조회 port. 시도별 실시간 측정값을 요약해 준다.
 *
 * <p>키 없음·호출/파싱 실패는 빈 Optional — 대기질은 부가 정보라 실패로 화면을 막지 않는다.
 */
public interface AirKoreaClient {

    /** 에어코리아 축약 시도명(예: "강원")의 실시간 대기질 요약. 없으면 빈 Optional. */
    Optional<AirQuality> realtimeBySido(String airKoreaSidoName);
}
