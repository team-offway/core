package com.offway.core.trip.infrastructure.datalab.dto;

import com.offway.core.trip.domain.VisitorType;
import java.time.LocalDate;

/**
 * 관광빅데이터 기초 시군구별 일별 방문자수 한 건(locgoRegnVisitrDDList item).
 *
 * @param signguCode 시군구 법정코드 (예: 11110)
 * @param signguName 시군구명 (예: 종로구) — region 매칭에 쓴다
 * @param baseDate 기준일자 — 요일(평일/주말)은 여기서 파생
 * @param type 방문자 구분 (현지인·외지인·외국인)
 * @param count 방문자수
 */
public record RegionVisitor(
        String signguCode, String signguName, LocalDate baseDate, VisitorType type, double count) {
}
