package com.offway.core.trip.service;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionIntroProvider;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.trip.domain.RegionPoi;
import com.offway.core.trip.domain.TripException;
import com.offway.core.trip.repository.RegionPoiRepository;
import com.offway.core.trip.service.dto.HomeResult;
import com.offway.core.trip.service.dto.RegionDetail;
import com.offway.core.trip.service.dto.RegionBenefit;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역 상세 화면 한 번을 채운다(#304).
 *
 * <p><b>외부를 부르지 않는다.</b> 재료가 전부 DB·메모리에 있다 — 장소 풀은 월 1회 배치가
 * ({@link RegionPoiRefreshService}), 대표 사진은 관광갤러리 적재분이, 소개는 부팅 시 한 번 만든 값이
 * 들고 있다. 코스 생성으로 우회하면 5.2초인데 그건 장소 선별 말고도 동선 최적화·이동시간을 다 치르기
 * 때문이고, 여기서 필요한 것은 "장소 목록" 하나다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> 각 조회가 자기 짧은 트랜잭션에서 끝나고, 넷 사이에 일관성이
 * 필요하지 않다 — 혜택과 장소가 서로 다른 순간의 값이어도 화면은 성립한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionDetailService {

    /**
     * 매력 포인트 장소 수 상한 — 시안 노트가 "최소 2개 ~ 최대 10개" 로 정했다.
     *
     * <p>최소는 서버가 만들 수 없다. 사진 있는 장소가 그 지역에 2개도 없으면 없는 것이고, 억지로 채우려면
     * 사진 없는 것을 섞어야 하는데 그건 회색 판을 만든다. 부족하면 부족한 대로 내리고 앱이 접는다.
     */
    private static final int MAX_HIGHLIGHT_SPOTS = 10;

    /** 서비스 기준 시간대. 혜택 매칭이 오늘 날짜를 보므로 서버 로케일에 맡기지 않는다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RegionQuery regionQuery;
    private final RegionPoiRepository regionPoiRepository;
    private final RegionIntroProvider regionIntroProvider;
    private final RegionHeroPhotoProvider regionHeroPhotoProvider;
    private final PolicyService policyService;
    private final CatchphraseProvider catchphraseProvider;
    private final RegionVisitMetricsService regionVisitMetricsService;

    /**
     * 그 지역의 상세.
     *
     * @throws TripException 없는 지역이면 {@code TRIP-002}(404)
     */
    public RegionDetail detail(long regionId) {
        Region region = regionQuery.byId(regionId)
                .orElseThrow(TripException::regionNotFound);

        List<RegionPoi> spots = regionPoiRepository.findShowable(regionId, MAX_HIGHLIGHT_SPOTS);
        if (spots.isEmpty()) {
            // 조용히 빈 목록을 내리면 "적재가 안 됐다" 와 "원래 없다" 가 구분되지 않는다.
            // 월 1회 배치가 안 돌았거나 그 지역만 실패했을 때 이 로그가 유일한 흔적이다.
            log.info("지역 상세 — 매력 포인트 장소가 없습니다 regionId={}", regionId);
        }

        return RegionDetail.builder()
                .regionId(region.getId())
                .sido(region.getSido())
                .sigungu(region.getSigungu())
                // 재료가 없으면 텍스트가 null 이고, 응답에서 그 필드가 사라진다(#140).
                .overview(regionIntroProvider.of(regionId).text())
                .photos(photosOf(regionId))
                .benefit(benefitOf(regionId))
                .highlightSpots(toSpots(spots))
                // 지명이 아니라 법정 시군구코드로 찾는다 — 동구 6곳·중구 6곳처럼 같은 이름이 전국에 여럿이다.
                .visitMetrics(regionVisitMetricsService.of(region.getLegalCode()))
                .build();
    }

    /**
     * 장소에 한 줄 소개를 붙인다(#87).
     *
     * <p>캐치프레이즈는 부팅 시 메모리에 든 4.5만 건이라 조회 비용이 없다. <b>없는 것이 정상이다</b> —
     * 그 목록에 없는 contentId 면 비고, 앱이 이름만 그린다.
     */
    private List<RegionDetail.Spot> toSpots(List<RegionPoi> spots) {
        return spots.stream()
                .map(poi -> RegionDetail.Spot.of(
                        poi, catchphraseProvider.forContentId(poi.getContentId()).orElse(null)))
                .toList();
    }

    /**
     * 대표 이미지 — <b>지금은 최대 한 장</b>이다.
     *
     * <p>목록으로 내리는 것은 화면이 여러 장을 그릴 수 있게 열어 둔 것이고(프론트 요청), 서버가 가진 것이
     * 늘면 그대로 늘어난다. 없으면 빈 목록이다 — {@code null} 을 내리면 앱이 길이를 묻기 전에 터진다.
     */
    private List<String> photosOf(long regionId) {
        // 여행월을 모르므로 계절 정렬은 하지 않는다(홈과 같은 판단).
        String hero = regionHeroPhotoProvider.heroPhotoUrls(List.of(regionId), null).get(regionId);
        return hero == null ? List.of() : List.of(hero);
    }

    /** 이 지역에 걸리는 혜택 하나 — 화면이 뱃지 한 개를 그린다. 홈 카드와 같은 규칙이다. */
    private RegionBenefit benefitOf(long regionId) {
        Map<Long, List<Policy>> matched =
                policyService.matchForRegions(List.of(regionId), LocalDate.now(SERVICE_ZONE));
        List<Policy> policies = matched.getOrDefault(regionId, List.of());
        if (policies.isEmpty()) {
            return null;
        }
        Policy first = policies.get(0);
        return RegionBenefit.from(first);
    }
}
