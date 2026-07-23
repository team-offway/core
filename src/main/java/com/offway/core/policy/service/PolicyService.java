package com.offway.core.policy.service;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyException;
import com.offway.core.policy.repository.PolicyRepository;
import com.offway.core.policy.service.dto.PolicyWithRegions;
import com.offway.core.region.domain.Region;
import com.offway.core.region.domain.RegionTagType;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.region.repository.RegionTagRepository;
import java.time.LocalDate;
import java.util.List;
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
    private final RegionTagRepository regionTagRepository;
    private final RegionRepository regionRepository;

    /**
     * 정책 상세 + 이 혜택이 되는 지역 목록(정책→지역 역방향). 미검증 정책은 노출하지 않는다.
     *
     * @throws PolicyException 정책이 없거나 미검증이면 404
     */
    public PolicyWithRegions getPolicyDetail(Long id) {
        Policy policy = policyRepository.findById(id)
                .filter(Policy::isVerified)
                .orElseThrow(PolicyException::notFound);
        List<Long> regionIds = regionTagRepository.findRegionIdsByTag(policy.getType().targetTag());
        List<Region> regions = regionRepository.findByIds(regionIds);
        return new PolicyWithRegions(policy, regions);
    }

    /**
     * 한 지역에 여행일 기준으로 적용 가능한 정책(뱃지) 목록. 지역 태그 ∩ 정책 대상 태그 + 기간 유효 + verified.
     *
     * <p>홈·추천의 혜택 뱃지가 이 결과를 쓴다.
     */
    public List<Policy> matchForRegion(Long regionId, LocalDate travelDate) {
        Set<RegionTagType> regionTags = Set.copyOf(regionTagRepository.findTagsByRegionId(regionId));
        return policyRepository.findAllVerified().stream()
                .filter(policy -> regionTags.contains(policy.getType().targetTag()))
                .filter(policy -> policy.isActiveOn(travelDate))
                .toList();
    }
}
