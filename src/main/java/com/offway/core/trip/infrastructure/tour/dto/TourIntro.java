package com.offway.core.trip.infrastructure.tour.dto;

/**
 * TourAPI 소개정보(detailIntro2)에서 뽑은 운영 정보. 필드명이 콘텐츠 타입마다 달라(관광지/문화시설/레포츠/음식점) 자유 텍스트로 담는다.
 *
 * @param contentId 콘텐츠 ID
 * @param useTime 이용시간/영업시간 (자유 텍스트, 없으면 null)
 * @param restDate 휴무일 (자유 텍스트, 없으면 null)
 */
public record TourIntro(String contentId, String useTime, String restDate) {
}
