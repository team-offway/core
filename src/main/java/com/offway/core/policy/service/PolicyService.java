package com.offway.core.policy.service;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyException;
import com.offway.core.policy.repository.PolicyRepository;
import com.offway.core.policy.service.dto.PolicyWithRegions;
import com.offway.core.region.domain.Region;
import com.offway.core.region.domain.RegionTagType;
import com.offway.core.region.service.RegionQuery;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정책 조회·매칭 조율. region 도메인은 그 port(region·region_tag 리포지토리)를 통해서만 참조한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final RegionQuery regionQuery;

    /**
     * 정책 상세 + 이 혜택이 되는 지역 목록(정책→지역 역방향). 미검증 정책은 노출하지 않는다.
     *
     * @throws PolicyException 정책이 없거나 미검증이면 404
     */
    public PolicyWithRegions getPolicyDetail(Long id) {
        Policy policy = policyRepository.findById(id)
                .filter(Policy::isVerified)
                .orElseThrow(PolicyException::notFound);
        List<Long> regionIds = regionQuery.idsWithTag(policy.getType().targetTag());
        List<Region> regions = regionQuery.byIds(regionIds);
        return new PolicyWithRegions(policy, regions);
    }

    /**
     * 한 지역에 여행일 기준으로 적용 가능한 정책(뱃지) 목록. 지역 태그 ∩ 정책 대상 태그 + 기간 유효 + verified.
     *
     * <p>홈·추천의 혜택 뱃지가 이 결과를 쓴다.
     */
    public List<Policy> matchForRegion(Long regionId, LocalDate travelDate) {
        Set<RegionTagType> regionTags = Set.copyOf(regionQuery.tagsOf(regionId));
        return match(regionTags, travelDate);
    }

    /**
     * 여러 지역의 혜택 뱃지를 <b>한 번에</b>. 홈·추천은 후보가 여럿이라 지역마다 {@link #matchForRegion} 을 부르면
     * 후보 수만큼 쿼리가 늘어난다 — 태그 조회도, 정책 전체 조회도 매번 다시 한다(N+1).
     *
     * <p>여기서는 정책 목록을 <b>한 번만</b> 읽고 태그도 IN 절 하나로 가져와, 후보가 20곳이든 6곳이든 쿼리는 2회다.
     *
     * @return 지역ID → 적용 가능한 정책 목록. 해당 지역에 맞는 정책이 없으면 <b>키가 없다</b>(빈 목록을 넣지 않는다)
     */
    public Map<Long, List<Policy>> matchForRegions(List<Long> regionIds, LocalDate travelDate) {
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        List<Policy> verified = policyRepository.findAllVerified();
        Map<Long, Set<RegionTagType>> tagsByRegion = regionQuery.tagsOf(regionIds);

        Map<Long, List<Policy>> matched = new HashMap<>();
        tagsByRegion.forEach((regionId, tags) -> {
            List<Policy> policies = match(verified, tags, travelDate);
            if (!policies.isEmpty()) {
                matched.put(regionId, policies);
            }
        });
        return matched;
    }

    private List<Policy> match(Set<RegionTagType> regionTags, LocalDate travelDate) {
        return match(policyRepository.findAllVerified(), regionTags, travelDate);
    }

    /** 매칭 규칙 한 곳 — 지역 태그 ∩ 정책 대상 태그 + 기간 유효. 단건·일괄이 같은 규칙을 쓰게 여기로 모은다. */
    private static List<Policy> match(List<Policy> verified, Set<RegionTagType> regionTags, LocalDate travelDate) {
        return verified.stream()
                .filter(policy -> regionTags.contains(policy.getType().targetTag()))
                .filter(policy -> policy.isActiveOn(travelDate))
                .toList();
    }
}
