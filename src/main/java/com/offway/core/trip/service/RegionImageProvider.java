package com.offway.core.trip.service;

import com.offway.core.trip.domain.RegionContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지역 대표 이미지 — 지역 카드가 쓰는 그 사진을 <b>다른 도메인에도</b> 내준다(#313).
 *
 * <p><b>왜 생겼나.</b> 내 코스 카드 사진이 "코스 첫 장소의 사진" 이라, 같은 공주시 코스인데 하나는 석탑,
 * 하나는 소나무숲으로 떴다. 코스를 다시 뽑으면 첫 장소가 바뀌기 때문이다. 카드가 대표하는 것은 그 코스가
 * 아니라 "내가 담은 공주시 여행" 이라, 지역이 같으면 사진도 같아야 목록에서 지역이 눈에 들어온다.
 *
 * <p><b>사다리는 지역 카드와 같다</b> — 갤러리에서 고른 대표 사진(#196)이 먼저고, 못 골랐으면 적재된 지역
 * 콘텐츠의 이미지로 내려간다. 코스 목록이 그 규칙을 다시 구현하면 두 화면의 사진이 갈릴 수 있어, 관광
 * 데이터를 소유한 이 도메인이 답을 준다.
 *
 * <p><b>둘 다 DB 만 읽는다.</b> 요청 경로에서 외부(TourAPI)를 부르지 않는다 — 목록 한 페이지가 지역 수만큼
 * 외부를 무는 모양이 되면 일일 한도가 그 화면 하나로 마른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionImageProvider {

    private final RegionHeroPhotoProvider regionHeroPhotoProvider;
    private final RegionContentProvider regionContentProvider;

    /**
     * 여러 지역의 대표 이미지를 <b>한 번에</b> 고른다.
     *
     * <p>지역마다 묻지 않는다 — 코스 목록은 페이지당 최대 100건이라, 코스마다 물으면 그게 곧 N+1 이다.
     * 중복 지역은 한 번만 조회한다(같은 지역 코스를 여럿 담는 것이 이 이슈의 출발점이다).
     *
     * @param regionIds 지역 id 들. 중복이 있어도 된다
     * @return 지역 id → 이미지 URL. <b>못 고른 지역은 키가 없다</b> — 화면이 사진 칸을 비운다
     */
    public Map<Long, String> imageUrls(List<Long> regionIds) {
        List<Long> distinct = regionIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> heroPhotos = regionHeroPhotoProvider.heroPhotoUrls(distinct, null);
        Map<Long, RegionContent> contents = regionContentProvider.storedForAll(distinct);

        Map<Long, String> images = new HashMap<>();
        for (Long regionId : distinct) {
            String hero = heroPhotos.get(regionId);
            String fallback = contents.getOrDefault(regionId, RegionContent.EMPTY).imageUrl();
            String chosen = hero != null ? hero : fallback;
            if (chosen != null) {
                images.put(regionId, chosen);
            }
        }
        // 못 고른 지역이 있으면 남긴다 — 사진 없는 카드가 조용히 늘어나는 것을 눈치채려면 흔적이 필요하다.
        if (images.size() < distinct.size()) {
            log.info("지역 대표 이미지 — 지역 {}곳 중 {}곳만 골랐습니다", distinct.size(), images.size());
        }
        return images;
    }
}
