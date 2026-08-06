package com.offway.core.trip.infrastructure.tour.dto;

import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.PoiContentType;
import com.offway.core.trip.domain.RegionContent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TourAPI 목록 조회 결과.
 *
 * @param items 이번 페이지의 관광지 목록
 * @param totalCount 전체 건수 — 지역 콘텐츠 충분성 판단(F3)에 쓴다
 */
public record TourPoiResult(List<TourPoi> items, int totalCount) {

    /** 외부에서 넘어온 가변 리스트가 바뀌어도 결과가 안 흔들리게 방어적 복사. */
    public TourPoiResult {
        items = List.copyOf(items);
    }

    private static final TourPoiResult EMPTY = new TourPoiResult(List.of(), 0);

    /** 지역 대표 사진으로 쓸 콘텐츠 타입. 숙박·음식점·쇼핑은 지역을 대표하지 않는다. */
    private static final Integer TOURIST_SPOT = PoiContentType.TOURIST_SPOT.contentTypeId();

    /** 키 없음·결과 없음 등 비어있는 결과. */
    public static TourPoiResult empty() {
        return EMPTY;
    }

    /**
     * 외부 응답을 도메인 콘텐츠로 변환한다(볼거리 수·대표 이미지·categories). 대표 이미지는 {@link #heroImage()},
     * categories 는 표본 POI 의 lclsSystm 대분류를 칩으로 매핑(중복 제거·발견 순서 유지)한다.
     */
    public RegionContent toRegionContent() {
        String imageUrl = heroImage();
        Set<Category> categories = new LinkedHashSet<>();
        for (TourPoi poi : items) {
            Category.fromLclsSystm1(poi.lclsSystm1()).ifPresent(categories::add);
        }
        return new RegionContent(totalCount, imageUrl, new ArrayList<>(categories), false);
    }

    /**
     * 지역 카드에 걸 대표 사진 — <b>관광지를 먼저</b> 찾고, 없으면 사진 있는 아무 곳이나.
     *
     * <p>예전에는 그냥 "사진 있는 첫 POI" 였다. 목록에 숙박·음식점·쇼핑이 섞여 오는데 정렬이 제목순이라,
     * 사실상 <b>이름이 가나다순으로 빠른 곳</b>이 지역을 대표했다 — 공주시는 책방, 부산 동구는 횟집이었다.
     * 화면에는 "여기 가고 싶다" 를 만들어야 할 자리에 남의 가게 앞마당이 걸렸다.
     *
     * <p>정렬을 조회순으로 바꿨으니(→ {@code arrange=B}) 위쪽이 인기 있는 곳이다. 거기서 <b>관광지</b>만
     * 골라 첫 사진을 쓴다. 실측(14개 지역 표본, 응답 성공 9곳)에서 표본 30건 안에 사진 있는 관광지가
     * <b>9/9</b> 로 있었다 — 이 때문에 API 를 한 번 더 부르지 않아도 된다.
     *
     * <p>그래도 폴백은 남긴다. 관광지가 하나도 없는 지역이 나오면 사진을 통째로 비우는 것보다 낫다.
     */
    private String heroImage() {
        return firstImageOf(poi -> TOURIST_SPOT.equals(poi.contentTypeId()))
                .or(() -> firstImageOf(poi -> true))
                .orElse(null);
    }

    private Optional<String> firstImageOf(Predicate<TourPoi> filter) {
        return items.stream()
                .filter(filter)
                .map(TourPoi::firstImage)
                .filter(image -> image != null && !image.isBlank())
                .findFirst();
    }
}
