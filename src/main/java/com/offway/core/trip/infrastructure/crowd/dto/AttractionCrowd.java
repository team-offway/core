package com.offway.core.trip.infrastructure.crowd.dto;

import java.time.LocalDate;

/**
 * 관광지 한 곳의 <b>하루치</b> 집중률 예측(#565).
 *
 * <p>이 API 는 관광지를 <b>날짜별로</b> 준다 — 관광지 하나가 30행이다. 그래서 이 record 한 건은
 * "그 관광지의 그 날짜" 를 가리킨다.
 *
 * <p><b>식별자가 이름뿐이다.</b> 콘텐츠 ID 를 주지 않아 우리 장소와는 이름으로 맞춘다. 실측(2026-09-14,
 * 표본 6곳)에서 정확 일치가 느슨 일치와 같았다(13/13 · 25/25 · 18/18) — 같은 공사 데이터라 표기가
 * 어긋나지 않는다. 그래서 정규화하지 않고 그대로 맞춘다.
 *
 * @param attractionName 관광지명({@code tAtsNm}) — 우리 {@code region_poi.title} 과 맞춘다
 * @param date 예측 날짜({@code baseYmd})
 * @param rate 집중률 0~100({@code cnctrRate}). 관광지별 정규화가 아니라 <b>관광지 간 비교가 되는</b>
 *     값이다 — 실측에서 관광지별 30일 평균이 3.0~88.9 로 흩어졌다
 */
public record AttractionCrowd(String attractionName, LocalDate date, double rate) {
}
