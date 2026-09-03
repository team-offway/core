package com.offway.core.policy.service.dto;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.region.domain.Region;
import java.util.List;

/**
 * 이 분류를 고르면 <b>어느 지역에 뜨는가</b>(#393).
 *
 * <p>지금까지 백오피스는 이걸 말하지 않았다. 편집 화면에 "대상 지역: 비수도권 인구감소지역" 한 줄이
 * 있었는데, <b>그게 몇 곳인지도 어디인지도</b> 화면 어디에도 없었다. 어드민은 정책을 켜면서
 * "이게 완도에 뜨나" 를 답할 수 없었고, 답하려면 코드를 읽어야 했다.
 *
 * <p>분류마다 대상이 크게 갈린다 — 숙박세일페스타 85곳, 반값여행 25곳, 나머지 다섯은 89곳 전부다.
 * 분류를 잘못 고르면 <b>85곳짜리가 25곳짜리로 조용히 줄어든다.</b>
 *
 * @param type 정책 분류
 * @param regions 이 분류가 닿는 지역 전부
 */
public record PolicyScope(PolicyType type, List<Region> regions) {

    public int regionCount() {
        return regions.size();
    }
}
