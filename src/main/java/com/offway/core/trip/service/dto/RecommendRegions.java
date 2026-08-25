package com.offway.core.trip.service.dto;

import com.offway.core.transport.domain.TransportMode;
import com.offway.core.trip.domain.Category;
import lombok.Builder;

/**
 * 여행지 추천 커맨드 — 서비스 내부용.
 *
 * @param originLat 출발지 위도
 * @param originLng 출발지 경도
 * @param transport 이동수단
 * @param maxReachMinutes 편도 도달 한계(분) — 가용시간(LNT) 산출 결과에서 온다
 * <p><b>조립이라 빌더다</b>(#300). 위도·경도가 같은 타입으로 나란히 있어 위치 인수로 넘기면
 * 뒤바뀌어도 컴파일이 통과한다 — 그 결과는 출발지가 엉뚱한 곳에 찍히는 것으로 나타난다.
 *
 * @param mood 무드칩(선택) — 지정 시 해당 카테고리 콘텐츠가 있는 지역을 앞세운다. 미지정·{@code ALL} 은 필터 없음
 */
@Builder
public record RecommendRegions(
        double originLat, double originLng, TransportMode transport, int maxReachMinutes, Category mood) {
}
