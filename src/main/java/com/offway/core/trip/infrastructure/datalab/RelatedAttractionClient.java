package com.offway.core.trip.infrastructure.datalab;

import com.offway.core.trip.infrastructure.datalab.dto.RelatedAttractionItem;
import java.time.YearMonth;
import java.util.List;

/**
 * 관광지별 연관 관광지 조회 port(#186) — {@code TarRlteTarService1}.
 *
 * <p>중심 관광지(#185)와 <b>실제로 함께 가는 곳</b>을 순위로 준다. 좌표 군집이 답하지 못하는 "왜 이
 * 조합인가" 가 여기 있다.
 *
 * <p><b>지역 밖은 어댑터가 거른다.</b> 원본이 인접 시군 것을 섞어 주는데(공주시 300건 중 45건),
 * 그 필터를 호출자에게 맡기면 한 곳만 잊어도 공주 코스에 천안 터미널이 들어간다.
 *
 * <p>키가 없으면 외부 호출 없이 빈 목록(로컬 실행성 불변식).
 */
public interface RelatedAttractionClient {

    /**
     * 그 지역의 연관 관광지 — <b>그 지역에 속한 것만</b>.
     *
     * @param legalCode 법정동 시군구코드 5자리. 데이터랩은 TourAPI 코드가 아니라 이것을 쓴다
     * @param sigunguName 우리 지역의 시군구명 — 응답의 {@code rlteSignguNm} 과 맞대 지역 밖을 거른다
     * @param baseMonth 원본 기준월
     * @param rows 한 번에 받을 건수
     */
    List<RelatedAttractionItem> findByRegion(
            String legalCode, String sigunguName, YearMonth baseMonth, int rows);
}
