package com.offway.core.trip.infrastructure.datalab;

import com.offway.core.trip.infrastructure.datalab.dto.HubAttractionItem;
import java.time.YearMonth;
import java.util.List;

/**
 * 지자체별 중심 관광지 조회 port — 한국관광 데이터랩({@code LocgoHubTarService1}).
 *
 * <p>구현은 {@code HubAttractionClientImpl}. 테스트는 stub 으로 갈아끼운다.
 */
public interface HubAttractionClient {

    /**
     * 한 지자체의 중심 관광지를 순위 순으로.
     *
     * @param legalCode 법정 시군구코드 5자리. {@code areaCd} 는 앞 2자리로 파생한다
     * @param baseMonth 기준 연월
     * @param rows 최대 건수(원본은 100위까지)
     * @return 순위 오름차순. 키가 없거나 해당 월이 미발행이면 빈 목록
     */
    List<HubAttractionItem> findByRegion(String legalCode, YearMonth baseMonth, int rows);
}
