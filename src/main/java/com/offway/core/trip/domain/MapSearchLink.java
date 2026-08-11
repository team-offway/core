package com.offway.core.trip.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 인허가 장소를 지도 검색으로 넘기는 링크(#161).
 *
 * <p><b>왜 링크인가.</b> 식당·카페·숙소는 인허가 데이터로 채우는데, 그 데이터는 "영업 허가를 받았다" 는 사실이
 * 목적이라 영업시간·사진·소개가 애초에 수집 대상이 아니다. 다른 데서 가져올 수도 없다 — 실측에서 TourAPI 는
 * 인허가 장소 20건 중 <b>0건</b>이 매칭됐고(동네 가게는 관광공사 등록 대상이 아니다), 네이버·카카오 검색 API 는
 * 좌표까지만 주고 상세는 자기 페이지로 보낸다. 공식 API 로 영업시간을 주는 곳은 사실상 구글 Places 뿐인데
 * 캐싱 30일 제한이 있어 "미리 받아 DB 에 둔다" 는 우리 방식과 충돌한다.
 *
 * <p><b>낡은 영업시간을 우리가 보여줄 위험이 없다는 것이 이 방식의 값어치다.</b> 가게 정보는 자주 바뀌고, 틀린
 * 영업시간은 없는 것보다 나쁘다.
 *
 * <p>키도 외부 호출도 비용도 없다. 이미 가진 상호와 소재지로 만든다.
 */
public final class MapSearchLink {

    /** 네이버 지도 검색. 기본 지도를 네이버로 가져가므로 사용자가 보던 지도에서 이어진다. */
    private static final String SEARCH_URL = "https://map.naver.com/p/search/";

    /**
     * 소재지에서 시군구를 떼어낼 때 보는 토큰 위치.
     *
     * <p>주소는 {@code 경상북도 의성군 의성읍 ...} 처럼 시도·시군구로 시작한다. 두 번째 토큰이 시군구다.
     */
    private static final int SIGUNGU_TOKEN_INDEX = 1;

    private MapSearchLink() {
    }

    /**
     * 지도 검색 링크. 상호가 없으면 만들지 않는다 — 빈 검색어로 지도를 여는 것은 의미가 없다.
     *
     * <p><b>상호만으로 검색하지 않는다.</b> {@code 메가엠지씨커피 공주대점} 같은 이름은 전국에 흩어져 있어 다른
     * 지점이 잡힌다. 소재지에서 시군구를 떼어 함께 넣는다. 소재지가 없으면 상호만으로 만든다 — 없는 것보다 낫다.
     */
    public static Optional<String> of(String name, String address) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sigungu = sigunguOf(address);
        String query = sigungu.isEmpty() ? name.trim() : sigungu + " " + name.trim();
        return Optional.of(SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    /** 소재지의 시군구. 없거나 토큰이 모자라면 빈 문자열. */
    private static String sigunguOf(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String[] tokens = address.trim().split("\\s+");
        return tokens.length > SIGUNGU_TOKEN_INDEX ? tokens[SIGUNGU_TOKEN_INDEX] : "";
    }
}
