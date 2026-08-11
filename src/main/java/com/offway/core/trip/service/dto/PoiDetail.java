package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.PoiIntro;

/**
 * 장소 상세 — 공통 정보 + 카테고리별 보조정보(#157).
 *
 * <p>보조정보는 {@link PoiIntro} 를 통째로 들고 다닌다. 여기서 카테고리별로 쪼개면 그 지식이 서비스와 응답
 * DTO 두 곳에 생긴다 — 쪼개는 일은 응답 DTO 가 한 번만 한다.
 *
 * <p>외부 어댑터의 DTO 가 아니라 도메인 타입을 든다. 관광 API 응답 모양이 바뀌어도 이 계약은 흔들리지 않는다.
 */
public record PoiDetail(
        String contentId,
        Integer contentTypeId,
        /**
         * 화면에 뱃지로 나갈 분류 — <b>출처가 아는 값을 그대로 들고 온다</b>(#239).
         *
         * <p>예전에는 응답 DTO 가 {@code contentTypeId} 로 라벨을 되찾았다. 그런데 우리 DB 에서 온 장소는
         * 그 코드가 "TourAPI 아님" 을 뜻하는 0 이라 전부 <b>"기타"</b> 로 떨어졌다 — 국보 `공주 마곡사
         * 오층석탑`이 "기타" 로, 인허가 12만 건이 통째로 "기타" 로 나갔다. 지어낼 값이 없어서가 아니라
         * 가진 값을 안 쓴 것이었다: 국가유산은 종목(국보·보물·사적), 인허가는 분류(한옥체험·전통사찰)를
         * 이미 들고 있다.
         */
        String typeLabel,
        String title,
        String address,
        String tel,
        Double lat,
        Double lng,
        String imageUrl,
        String overview,
        /** 카테고리별 보조정보. 우리 DB 출처(인허가·국가유산)는 없어 null 이다. */
        PoiIntro intro,
        String catchphrase) {

    /** 관광 API 콘텐츠가 아닌 장소 — 보조정보가 없다. */
    public static PoiDetail withoutIntro(
            String contentId, Integer contentTypeId, String typeLabel, String title, String address, String tel,
            Double lat, Double lng, String imageUrl, String overview) {
        return new PoiDetail(
                contentId, contentTypeId, typeLabel, title, address, tel, lat, lng, imageUrl, overview, null, null);
    }
}
