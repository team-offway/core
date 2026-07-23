package com.offway.core.policy.service.dto;

import com.offway.core.policy.domain.Policy;
import com.offway.core.region.domain.Region;
import java.util.List;

/**
 * 정책 + 이 혜택이 되는 지역 목록 (정책→지역 역방향 결과). 서비스 내부 result.
 *
 * @param policy 정책
 * @param regions 이 정책의 대상 태그가 붙은 지역들
 */
public record PolicyWithRegions(Policy policy, List<Region> regions) {
}
