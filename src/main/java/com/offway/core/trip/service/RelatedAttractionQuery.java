package com.offway.core.trip.service;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.RelatedAttraction;
import com.offway.core.trip.repository.RelatedAttractionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * "실제로 함께 가는 곳" 순서를 코스에 알려주는 통로(#186).
 *
 * <p>itinerary 는 이 서비스로만 연관 데이터를 얻는다 — 다른 도메인이 trip 의 저장소를 직접 보지
 * 않는다(도메인 경계).
 *
 * <p><b>DB 만 읽는다.</b> 적재는 월 1회 배치가 하고({@link RelatedAttractionRefreshService}) 여기는
 * 요청 경로라 외부를 부르지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelatedAttractionQuery {

    /** 원본 대분류 — 코스의 볼거리 슬롯에 들어갈 것들. */
    private static final String CATEGORY_SIGHT = "관광지";

    /** 원본 대분류 — 끼니 자리. #161 이 네이버 리뷰 수로 풀려던 문제의 더 나은 답이다. */
    private static final String CATEGORY_FOOD = "음식";

    private final RelatedAttractionRepository relatedAttractionRepository;

    /**
     * 그 지역에서 <b>함께 가는 정도가 큰 순서대로</b> 볼거리 후보 식별자.
     *
     * @return 코스 후보의 {@code contentId} 와 같은 모양({@code LIC-} 접두어). 연관 데이터가 없으면
     *     <b>빈 목록</b>이고, 호출자는 좌표 군집으로 되돌아간다
     */
    public List<String> sightPlaceIds(long regionId) {
        return orderedPlaceIds(regionId, CATEGORY_SIGHT).stream()
                .map(LicensedPlace::publicId)
                .toList();
    }

    /** 같은 규칙으로 끼니 자리 — "그 관광지 가는 사람이 실제로 들르는 식당" 이다. */
    public List<String> foodPlaceIds(long regionId) {
        return orderedPlaceIds(regionId, CATEGORY_FOOD).stream()
                .map(LicensedPlace::publicId)
                .toList();
    }

    /**
     * 끼니 자리의 연관 장소를 <b>내부 ID</b> 로(#527) — 후보 풀에 실을 때 쓴다.
     *
     * <p>공개 식별자가 아니라 내부 ID 인 이유는 이 값으로 인허가 장소를 다시 읽어야 해서다. 순서는
     * {@link #foodPlaceIds} 와 같다.
     *
     * <p><b>왜 풀에 따로 실어야 하나</b> — 인허가 후보는 적합도·이름순 상위 100건만 올라오는데, 연관
     * 카페 375곳 중 <b>139곳(37%)이 그 컷 밖</b>이다(실측 2026-09-08). 순서만 바꾸면 그 139곳은
     * 영영 안 뽑힌다.
     *
     * <p>카페와 식당이 섞여 있다 — 원본 분류가 {@code 음식} 하나뿐이라 여기서는 못 가른다. 어느 쪽인지는
     * 이 ID 로 읽은 장소의 {@code kind} 가 답한다.
     */
    public List<Long> foodPlaceRawIds(long regionId) {
        return orderedPlaceIds(regionId, CATEGORY_FOOD);
    }

    /**
     * 중심 관광지를 가로질러 순위 순으로 편다.
     *
     * <p><b>중심이 여럿이면 순위가 겹친다</b> — 갑사의 1위와 마곡사의 1위가 둘 다 1이다. 그때는
     * 중심 이름으로 갈라 <b>같은 입력이 같은 순서</b>를 내게 한다. 회차마다 순서가 바뀌면 같은 요청이
     * 다른 코스를 내고, 사용자는 그 목록을 못 믿는다.
     */
    private List<Long> orderedPlaceIds(long regionId, String categoryLarge) {
        List<RelatedAttraction> rows = relatedAttractionRepository.findByRegion(regionId).stream()
                .filter(row -> categoryLarge.equals(row.getCategoryLarge()))
                .sorted(Comparator.comparingInt(RelatedAttraction::getRelatedRank)
                        .thenComparing(RelatedAttraction::getHubName)
                        .thenComparing(RelatedAttraction::getRelatedName))
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        // 같은 장소가 여러 중심에 걸릴 수 있다 — 가장 높은 순위 한 번만 남긴다.
        Set<Long> seen = new LinkedHashSet<>();
        List<Long> ids = new ArrayList<>();
        for (RelatedAttraction row : rows) {
            Long placeId = row.getLicensedPlaceId();
            if (seen.add(placeId)) {
                ids.add(placeId);
            }
        }
        return ids;
    }
}
