package com.offway.core.trip.service;

import com.offway.core.trip.domain.GalleryPhoto;
import com.offway.core.trip.domain.HubAttraction;
import com.offway.core.trip.repository.GalleryPhotoRepository;
import com.offway.core.trip.repository.HubAttractionRepository;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지역 대표 사진을 고른다 — 중심 관광지 순위 × 관광사진 갤러리(#196).
 *
 * <p><b>왜 이렇게 고르나.</b> 지역 카드 사진이 예전에는 "TourAPI 목록의 첫 사진" 이었다. 제목순이라 가가책방·
 * 감포참가자미가 걸렸고(#182), 조회순으로 고친 뒤에도 그 지역의 <b>대표</b>인지는 답하지 못했다. 중심 관광지는
 * 실제 이동 데이터로 매긴 순위라 그 답에 가장 가깝다(#185).
 *
 * <p><b>1위를 그대로 쓰지 않는다.</b> 89곳 전수 실측(2026-08-09)에서 1위가 대표감이 아닌 경우가 흔했다.
 *
 * <pre>
 *   대구 남구  1위 서부시외버스터미널 [관광지/기타관광]
 *   대구 서구  1위 서대구역          [관광지/기타관광]
 *   장수군    1위 장수골프리조트     [관광지/레저스포츠]
 * </pre>
 *
 * <p>그래서 <b>순위대로 훑어 갤러리에 사진이 있는 첫 항목</b>을 고른다. 사진작가가 터미널·시장을 찍지 않으므로
 * 갤러리 존재 여부가 자연 필터가 된다 — 전수 실측에서 골프장·리조트·콘도·카지노는 한 곳도 뽑히지 않았다.
 * 그래도 뚫리는 것(부산역·단양구경시장)이 있어 이름 기반 제외를 함께 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionHeroPhotoProvider {

    /**
     * 지역 <b>대표</b>로 세우지 않을 시설 — 갤러리에 사진이 있어도 제외한다.
     *
     * <p>실측에서 이 규칙이 바꾼 곳: 부산 동구(부산역 → 부산항), 단양군(단양구경시장 → 만천하스카이워크).
     * 교통·숙박·상업시설은 "그 지역에 왜 가는가" 에 답하지 못한다.
     */
    private static final Pattern NOT_HERO = Pattern.compile(
            "골프|컨트리클럽|CC$|리조트|콘도|호텔|모텔|펜션|카지노|터미널|역$|시장|백화점|아울렛|마트|병원|대학교|스키장|찜질|사우나");

    /** 이름에서 지점·부가 표기를 떼는 구분자 — "화본역/폐역"·"신비동물원/가평점". */
    private static final Pattern NAME_SUFFIX = Pattern.compile("[/(].*$");

    /** 매칭에서 무시하는 문자 — 표기 흔들림(띄어쓰기·가운뎃점)을 흡수한다. */
    private static final Pattern IGNORED_CHARS = Pattern.compile("[\\s\\-·,]");

    /**
     * 중심 관광지와 못 이었을 때 "그 지역 사진 아무거나" 로 내려가는 최소 보유량.
     *
     * <p>실측(2026-08-09) 분포에서 정했다 — 이 값을 넘는 곳(동구 30·영암 13·옹진 8·의령 5)은 그 지역을
     * 대표할 사진이 실제로 쌓여 있었고, 미만인 곳(영양 1·의성 2)은 '가마' 처럼 우연히 찍힌 한 장이었다.
     */
    private static final int MIN_REGION_POOL = 5;

    private final GalleryPhotoRepository galleryPhotoRepository;
    private final HubAttractionRepository hubAttractionRepository;

    /**
     * 여러 지역의 대표 사진을 <b>한 번에</b> 고른다.
     *
     * <p>목록 화면(홈·추천)이 지역마다 묻지 않게 두 테이블을 각각 한 번씩만 읽는다 — 지역마다 조회하면
     * 카드 20장에 40번 질의가 나간다.
     *
     * @param travelMonth 여행월. 있으면 촬영월이 가까운 사진을 고른다. 모르면 null
     * @return 지역 id → 대표 사진 URL. <b>못 고른 지역은 아예 없다</b> — 호출자가 TourAPI 로 폴백한다
     */
    public Map<Long, String> heroPhotoUrls(List<Long> regionIds, YearMonth travelMonth) {
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<GalleryPhoto>> photosByRegion = galleryPhotoRepository.findByRegionIds(regionIds).stream()
                .collect(Collectors.groupingBy(GalleryPhoto::getRegionId));
        Map<Long, List<HubAttraction>> hubsByRegion = hubAttractionRepository.findByRegionIds(regionIds).stream()
                .collect(Collectors.groupingBy(HubAttraction::getRegionId));

        Map<Long, String> urls = new HashMap<>();
        for (Long regionId : regionIds) {
            heroPhotoUrl(
                            hubsByRegion.getOrDefault(regionId, List.of()),
                            photosByRegion.getOrDefault(regionId, List.of()),
                            travelMonth)
                    .ifPresent(url -> urls.put(regionId, url));
        }
        return urls;
    }

    /**
     * 한 지역의 대표 사진 URL.
     *
     * @param hubAttractions 그 지역의 중심 관광지(순위 오름차순)
     * @param photos 그 지역에 붙은 갤러리 사진
     * @return 대표 사진 URL. 못 고르면 empty
     */
    private Optional<String> heroPhotoUrl(
            List<HubAttraction> hubAttractions, List<GalleryPhoto> photos, YearMonth travelMonth) {
        if (photos.isEmpty()) {
            return Optional.empty();
        }
        for (HubAttraction attraction : hubAttractions) {
            if (!isHeroCandidate(attraction)) {
                continue;
            }
            Optional<GalleryPhoto> matched = bestPhotoFor(attraction, photos, travelMonth);
            if (matched.isPresent()) {
                return matched.map(GalleryPhoto::getImageUrl);
            }
        }
        return anyRegionPhoto(photos, travelMonth).map(GalleryPhoto::getImageUrl);
    }

    /**
     * 중심 관광지와 이어지지 않을 때 — <b>그 지역 사진이 충분히 쌓였으면</b> 그중 한 장을 쓴다.
     *
     * <p>중심 관광지 이름과 갤러리 제목이 안 맞는다고 해서 그 지역에 쓸 사진이 없는 것은 아니다. 실측
     * (2026-08-09)에서 영암군이 그랬다 — 중심 관광지 3위가 도갑사라 그걸 찾는데, 정작 영암을 대표하는
     * <b>월출산</b> 사진이 갤러리에 13장 있었다. 부산 동구도 산복도로·이바구길이 30장 쌓여 있었다.
     *
     * <p><b>{@value #MIN_REGION_POOL}장 이상일 때만</b> 쓴다. 한두 장뿐인 곳은 그 지역을 대표할 사진이
     * 있다기보다 우연히 한 장 찍힌 쪽에 가깝다 — 실측에서 영양군의 유일한 사진은 '가마' 였고, 그럴 바엔
     * TourAPI 로 내려가는 편이 낫다.
     */
    private static Optional<GalleryPhoto> anyRegionPhoto(List<GalleryPhoto> photos, YearMonth travelMonth) {
        if (photos.size() < MIN_REGION_POOL) {
            return Optional.empty();
        }
        return bestBySeason(photos, travelMonth);
    }

    /** 대표로 세울 만한 항목인가 — 관광지 대분류이면서 교통·숙박·상업시설이 아니어야 한다. */
    private static boolean isHeroCandidate(HubAttraction attraction) {
        if (!attraction.isSight()) {
            return false;
        }
        return !NOT_HERO.matcher(baseName(attraction.getName())).find();
    }

    /**
     * 그 장소의 사진 중 가장 좋은 한 장.
     *
     * <p>여행월을 알면 <b>촬영월이 가까운</b> 것을 고른다 — 10월 여행에 설경을 내보내지 않는다. 촬영월이
     * 없는 사진은 뒤로 밀되 버리지는 않는다(한 장뿐일 수 있다).
     */
    private static Optional<GalleryPhoto> bestPhotoFor(
            HubAttraction attraction, List<GalleryPhoto> photos, YearMonth travelMonth) {
        String needle = normalized(baseName(attraction.getName()));
        if (needle.isBlank()) {
            return Optional.empty();
        }
        List<GalleryPhoto> matched = photos.stream()
                .filter(photo -> normalized(photo.matchableText()).contains(needle))
                .toList();
        return bestBySeason(matched, travelMonth);
    }

    /** 여행월과 계절이 가까운 한 장. 여행월을 모르면 첫 장. */
    private static Optional<GalleryPhoto> bestBySeason(List<GalleryPhoto> photos, YearMonth travelMonth) {
        if (photos.isEmpty()) {
            return Optional.empty();
        }
        if (travelMonth == null) {
            return Optional.of(photos.getFirst());
        }
        return photos.stream().min(Comparator.comparingInt(photo -> seasonDistance(photo, travelMonth)));
    }

    /**
     * 촬영월과 여행월의 <b>계절 거리</b>(0~6) — 연도는 무시하고 달만 본다.
     *
     * <p>12월과 1월은 한 달 차이로 봐야 한다. 촬영월을 모르면 가장 먼 값으로 둬 뒤로 밀린다.
     */
    private static int seasonDistance(GalleryPhoto photo, YearMonth travelMonth) {
        return photo.photographyMonth()
                .map(taken -> {
                    int diff = Math.abs(taken.getMonthValue() - travelMonth.getMonthValue());
                    return Math.min(diff, 12 - diff);
                })
                .orElse(Integer.MAX_VALUE);
    }

    private static String baseName(String name) {
        return NAME_SUFFIX.matcher(name).replaceAll("").strip();
    }

    private static String normalized(String text) {
        return IGNORED_CHARS.matcher(text).replaceAll("");
    }
}
