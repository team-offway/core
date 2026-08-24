package com.offway.core.trip.infrastructure.tour.dto;

import com.offway.core.trip.domain.PoiIntro;
import lombok.Builder;

/**
 * 소개정보(detailIntro2) 응답 — <b>카테고리마다 다른 것</b>을 함께 들고 온다.
 *
 * <p>예전에는 운영시간·휴무일 둘만 뽑았다. 그런데 같은 엔드포인트가 카테고리별로 훨씬 많은 것을 준다 —
 * 음식점은 대표메뉴·취급메뉴, 숙박은 입실·퇴실 시각과 객실 수, 문화시설은 이용요금. 우리 89곳에서 실측한
 * 채움률이 아래와 같아, 안 읽고 버리기엔 아까운 값들이다(카테고리당 12개 지역 24건 표본, 2026-08-11).
 *
 * <table>
 *   <caption>우리 89곳 기준 채움률</caption>
 *   <tr><th>카테고리</th><th>채워지는 것</th></tr>
 *   <tr><td>음식점</td><td>영업시간 100% · 휴무일 100% · 대표메뉴 95% · 취급메뉴 95%</td></tr>
 *   <tr><td>문화시설</td><td>이용시간 100% · 휴무일 91% · 요금 87% · 주차 87%</td></tr>
 *   <tr><td>숙박</td><td>입실·퇴실 100% · 객실수 100% · 예약안내 33%</td></tr>
 *   <tr><td>관광지</td><td>이용시간 100% · 휴무일 100% · 주차 87%</td></tr>
 *   <tr><td>레포츠</td><td>표본 24건이 전부 빔(캠핑장·낚시터 위주). 편차가 커서 값이 오면 그대로 쓴다</td></tr>
 * </table>
 *
 * <p><b>비어 있는 것은 null 이다.</b> 카테고리가 애초에 안 주는 필드(음식점의 요금 등)와 값이 없는 것을
 * 구분하지 않는다 — 화면은 둘 다 "그 줄을 지운다" 로 같게 다루기 때문이다.
 */
@Builder
public record TourIntro(
        String contentId,
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
        String reservation,
        /** 체험안내(체험관광) — 체험 카드 부제(#305). */
        String experienceGuide) {

    /**
     * 도메인 보조정보로 옮긴다 — 외부 응답이 상위 레이어로 새지 않게(매핑 규약: 외부 결과 객체의 {@code toXxx()}).
     *
     * <p>{@code contentId} 는 넘기지 않는다. 이미 장소 자신이 들고 있어 보조정보가 다시 가질 이유가 없다.
     */
    public PoiIntro toPoiIntro() {
        return PoiIntro.builder()
                .useTime(useTime)
                .restDate(restDate)
                .parking(parking)
                .fee(fee)
                .signatureMenu(signatureMenu)
                .menus(menus)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .roomCount(roomCount)
                .reservation(reservation)
                .experienceGuide(experienceGuide)
                .build();
    }
}
