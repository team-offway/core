package com.offway.core.trip.service;

import com.offway.core.common.response.Paging;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionMaster;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.service.dto.RegionList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 지역 목록 조회(#266) — "이번달 추천 여행지 더보기" 가 쓰는 길이다.
 *
 * <p><b>왜 생겼나.</b> 목록 전용 API 가 없어 그 화면이 홈({@code GET /api/v1/home}) 응답을 재사용했는데, 홈은 랭킹 상위 6곳만 준다.
 * 89곳을 페이지로 끊어 주고 카테고리로 좁힐 수 있어야 한다.
 *
 * <p><b>외부를 부르지 않는다.</b> 이 목록의 재료는 전부 이미 우리 DB 에 있다 — 한산도는 방문자 집계({@link RegionRankingService}),
 * 볼거리 수·카테고리·이미지는 적재된 지역 콘텐츠({@link RegionContentProvider#storedForAll}), 대표 사진은 관광사진 갤러리
 * ({@link RegionHeroPhotoProvider}). 페이지마다 외부를 부르면 더보기 몇 번으로 TourAPI 일일 한도가 마른다.
 *
 * <p><b>트랜잭션으로 감싸지 않는다.</b> {@link RegionRankingService} 는 집계가 통째로 비어 있을 때(새 환경 첫 요청) 한 번 외부를
 * 부르는 경로를 갖고 있다. 여기를 {@code @Transactional} 로 묶으면 그 호출이 트랜잭션 안에 들어가 read-timeout 동안 DB 커넥션을
 * 잡는다(영속성 규약). 협력자들이 각자 짧은 트랜잭션을 갖는다 — {@link HomeService} 와 같은 판단이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionListService {

    private final RegionMaster regionMaster;
    private final RegionRankingService regionRankingService;
    private final RegionContentProvider regionContentProvider;
    private final RegionHeroPhotoProvider regionHeroPhotoProvider;

    /**
     * 인구감소지역을 방문자 랭킹 순으로 한 페이지 준다.
     *
     * <p><b>정렬은 하나뿐이라 파라미터로 받지 않는다.</b> 홈·추천이 쓰는 그 랭킹(베이지안 보정 방문자수)이다. 도달시간 순은 출발지
     * 좌표가 있어야 정의되는데 이 엔드포인트는 그것을 받지 않는다 — 그쪽은 {@code POST /api/v1/regions/recommendations} 가 이미
     * 소유한다. 근거 없는 정렬 키를 열어 두는 것이 제일 나쁘다.
     *
     * @param category 필터칩. null 또는 {@link Category#ALL} 이면 전체
     * @param page 0부터. 해석(기본값·상한·자르기)은 {@link Paging} 이 단독으로 소유한다
     * @param size 페이지 크기. 위와 같다
     */
    public RegionList list(Category category, Integer page, Integer size) {
        PageRequest pageRequest = Paging.of(page, size);
        List<Region> all = regionMaster.all();
        if (all.isEmpty()) {
            return RegionList.from(new PageImpl<>(List.of(), pageRequest, 0));
        }

        List<Long> allIds = all.stream().map(Region::getId).toList();
        // 89행 한 번. 필터와 카드 재료가 같은 값이라 페이지 것만 읽어 봐야 필터를 걸 수 없다.
        Map<Long, RegionContent> contents = regionContentProvider.storedForAll(allIds);
        Map<Long, Region> regionById = new HashMap<>();
        all.forEach(region -> regionById.put(region.getId(), region));

        Category filter = category == null ? Category.ALL : category;
        // 정렬·필터·페이지 자르기를 메모리에서 한다. 대상이 고시로 정해진 89곳 고정이고, 랭킹 점수가 DB 컬럼이
        // 아니라 전체 표본으로 계산되는 값이라 SQL 로 내릴 수 있는 정렬이 아니다.
        List<RegionScore> matched = regionRankingService.rankByVisitors(all).stream()
                .filter(score -> contentOf(contents, score.regionId()).has(filter))
                .toList();

        List<RegionScore> pageScores = slice(matched, pageRequest);
        List<Long> pageIds = pageScores.stream().map(RegionScore::regionId).toList();
        // 대표 사진은 <b>이 페이지 것만</b> 고른다 — 89곳 전부를 고르면 안 보여줄 카드까지 계산한다.
        Map<Long, String> heroPhotos = regionHeroPhotoProvider.heroPhotoUrls(pageIds, null);

        List<RegionList.Item> items = pageScores.stream()
                .map(score -> toItem(regionById.get(score.regionId()), score, contents, heroPhotos))
                .toList();
        log.debug("지역 목록 category={} 전체={} 필터후={} 페이지={}건", filter, all.size(), matched.size(), items.size());
        return RegionList.from(new PageImpl<>(items, pageRequest, matched.size()));
    }

    private static RegionList.Item toItem(
            Region region, RegionScore score, Map<Long, RegionContent> contents, Map<Long, String> heroPhotos) {
        return RegionList.Item.of(
                region.getId(),
                region.getSido(),
                region.getSigungu(),
                score.crowdLevel(),
                contentOf(contents, region.getId()),
                heroPhotos.get(region.getId()));
    }

    /** 아직 적재되지 않은 지역은 빈 콘텐츠다 — 목록에서 빠지지 않고 볼거리 0 으로 나간다. */
    private static RegionContent contentOf(Map<Long, RegionContent> contents, long regionId) {
        return contents.getOrDefault(regionId, RegionContent.EMPTY);
    }

    /**
     * 페이지 범위만큼 잘라 낸다.
     *
     * <p>범위를 벗어난 페이지는 빈 목록이다 — 거절하지 않는다({@link Paging} 과 같은 판단). 마지막 페이지 다음을 요청하는 것은
     * 무한 스크롤에서 정상적으로 일어나는 일이라 400 으로 끊을 이유가 없다.
     */
    private static <T> List<T> slice(List<T> all, PageRequest pageRequest) {
        int from = (int) Math.min(pageRequest.getOffset(), all.size());
        int to = Math.min(from + pageRequest.getPageSize(), all.size());
        return all.subList(from, to);
    }
}
