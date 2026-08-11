package com.offway.core.trip.domain;

import lombok.Builder;

/**
 * 장소의 카테고리별 보조정보 — 운영시간·휴무일과 카테고리마다 다른 것들(#157).
 *
 * <p><b>관광 API 응답 모양과 같지만 이 자리에 따로 둔다.</b> 서비스와 응답 DTO 가 외부 어댑터의 DTO
 * ({@code infrastructure.tour.dto.TourIntro})를 직접 들면 외부 세부가 상위 레이어로 새고, 그쪽 응답 필드가
 * 바뀌는 순간 컨트롤러까지 흔들린다. 변환은 외부 결과 객체가 한다({@code TourIntro.toPoiIntro()}) —
 * "외부 응답 → 도메인은 외부 결과 객체의 toXxx()" 라는 매핑 규약 그대로다.
 *
 * <p><b>카테고리별로 쪼개지 않는다.</b> 쪼개는 지식(어느 카테고리가 어느 필드를 쓰는가)은 응답 DTO 한 곳에만
 * 둔다. 여기서 미리 나누면 같은 지식이 두 자리에 생긴다.
 *
 * <p><b>비어 있는 것은 null 이다.</b> 카테고리가 애초에 안 주는 필드(음식점의 요금 등)와 값이 없는 것을
 * 구분하지 않는다 — 화면은 둘 다 "그 줄을 지운다" 로 같게 다룬다.
 */
@Builder
public record PoiIntro(
        /** 이용·영업 시간. 카테고리마다 필드명이 다르지만 뜻이 같아 한 자리에 모은다. */
        String useTime,
        String restDate,
        /** 주차 가능 여부·요금 안내(관광지·문화시설·레포츠). */
        String parking,
        /** 이용요금(문화시설·레포츠). 관광지에는 이 필드가 없다. */
        String fee,
        /** 대표메뉴(음식점). */
        String signatureMenu,
        /** 취급메뉴 목록(음식점). 슬래시로 이어진 한 줄로 온다. */
        String menus,
        /** 입실 시각(숙박). */
        String checkIn,
        /** 퇴실 시각(숙박). */
        String checkOut,
        /** 객실 수(숙박). `5` 처럼 숫자만 오기도 하고 `5실` 로 오기도 해 문자열로 둔다. */
        String roomCount,
        /** 예약 안내(숙박). */
        String reservation) {
}
