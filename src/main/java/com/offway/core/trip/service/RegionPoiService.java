package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceKind;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.domain.FestivalPeriod;
import com.offway.core.trip.domain.PoiContentType;
import com.offway.core.trip.repository.FestivalPeriodRepository;
import java.time.LocalDate;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.domain.FestivalPlace;
import com.offway.core.trip.repository.FestivalPlaceRepository;
import com.offway.core.trip.repository.HeritagePlaceRepository;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.service.dto.RegionPois;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역의 코스 후보 POI 를 모아 볼거리·맛집·숙박 풀로 분류한다(course-logic ①). itinerary 가 코스를 짤 때 이 port 로만 POI 를
 * 얻는다 — 다른 도메인이 TourAPI 를 직접 부르지 않는다(도메인 경계).
 *
 * <p>TourAPI 는 read-timeout 이 길어 <b>트랜잭션 밖</b>에서 호출한다(persistence-convention). 키가 없으면 빈 결과
 * (로컬 실행성).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionPoiService {

    /** 한 번에 끌어오는 후보 수 — 분류 후 itinerary 가 필요 수만큼 고른다. */
    private static final int CANDIDATE_ROWS = 100;

    // TourAPI contentTypeId → 풀. (12 관광지·14 문화시설·15 축제공연행사·28 레포츠 = 볼거리, 39 음식점, 32 숙박)
    private static final Set<Integer> SIGHT_TYPES = Set.of(12, 14, 15, 28);

    /**
     * 대분류({@code lclsSystm1}) — <b>풀을 가르는 실제 기준</b>(#304).
     *
     * <p>{@code contentTypeId} 와 어긋나는 값이 있어 그걸로만 가르면 새는 장소가 생긴다.
     * 실측(2026-08-21 · 89곳 전수): {@code AC}(숙박) 977건 중 <b>625건이 타입 28</b>(레포츠)이었다 —
     * 전부 야영장·캠핑장·펜션이다. 지방일수록 그 비중이 커서 "잘 곳 없는 코스" 의 원인이었다.
     */
    private static final String LCLS_STAY = "AC";

    private static final String LCLS_FOOD = "FD";

    /**
     * 중분류로만 갈리는 잘 곳 — <b>복합관광시설(리조트)</b>(#304).
     *
     * <p>실측에서 카라반·글램핑 리조트 39건이 대분류 {@code VE}(문화관광)로 왔다. 대분류만 보면 볼거리에
     * 남는데, 사용자가 거기서 잔다. 사진 보유율 100% 라 숙박 풀이 얇은 지역에서 특히 값어치가 있다.
     */
    private static final String LCLS_RESORT = "VE05";
    private static final int FOOD_TYPE = 39;
    private static final int STAY_TYPE = 32;

    /** TourAPI 콘텐츠가 아님을 뜻하는 타입 — 인허가·국가유산이 함께 쓴다. 실제 contentTypeId 는 12·32·39 처럼 모두 양수다. */
    private static final int NON_TOUR_CONTENT_TYPE = 0;

    /** 축제 콘텐츠 타입 — 이 타입만 기간을 물어본다(#388). */
    private static final int FESTIVAL_TYPE = PoiContentType.FESTIVAL.contentTypeId();

    /**
     * 여행일에 열리는 축제를 몇 건까지 볼거리로 올릴 것인가(#433).
     *
     * <p>후보 100건과 달리 작게 잡는다. 이 목록은 <b>이미 그날로 걸러진 것</b>이라 한 지역에서 보통
     * 0~2건이고, 많아 봐야 큰 지역의 겹치는 행사 몇 건이다. 상한이 필요한 이유는 한 지역이 축제로만
     * 채워져 다른 볼거리를 밀어내지 않게 하려는 것이다.
     */
    private static final int OPEN_FESTIVAL_LIMIT = 5;

    private final RegionQuery regionQuery;
    private final TourApiClient tourApiClient;
    private final CatchphraseProvider catchphraseProvider;
    private final LicensedPlaceRepository licensedPlaceRepository;
    private final HeritagePlaceRepository heritagePlaceRepository;
    private final FestivalPeriodRepository festivalPeriodRepository;
    private final FestivalPlaceRepository festivalPlaceRepository;

    /**
     * 지역의 후보 POI 를 세 풀로 분류해 돌려준다. 좌표가 없는 POI 는 지도·동선에 못 쓰므로 제외한다.
     *
     * <p><b>여행일을 받는 이유</b>(#388) — 볼거리 풀에는 축제(타입 15)가 섞여 있는데, 그날 안 하는 축제는
     * 갈 수 없는 곳이다. 날짜를 인자로 둬서 <b>호출자가 "언제 가는 코스인가" 에 답하게</b> 한다.
     */
    public RegionPois collect(long regionId, LocalDate travelDate) {
        Region region = regionQuery.byId(regionId).orElse(null);
        if (region == null) {
            log.debug("코스 POI 수집 — 없는 지역 regionId={}", regionId);
            return RegionPois.empty();
        }

        // 볼거리·맛집·숙박을 각각 타입 스코프로 조회한다. 전체타입을 한 번만 뽑으면 인구감소지역처럼 등록 수가 적은 곳에서
        // 맛집·숙박이 볼거리에 밀려 과소표집돼(끼니·숙소가 코스에서 빠짐), 풀마다 독립 조회로 채운다.
        //
        // **전체타입 응답은 대분류(lclsSystm1)로 가른다**(#304). contentTypeId 로 가르면 야영장·캠핑장이
        // 볼거리로 샌다 — 실측(89곳 전수)에서 AC05(숙박) 625건이 타입 28(레포츠)로 왔다. 숙박 조회
        // (contentTypeId=32)에는 안 잡히는 값이라, 그동안 지방 숙소가 통째로 빠지고 있었다.
        List<PoiCandidate> allTypes = candidates(region, null);
        List<PoiCandidate> sights =
                withoutClosedFestivals(allTypes.stream().filter(c -> isSight(c)).toList(), travelDate);
        List<PoiCandidate> foods = merge(allTypes.stream().filter(c -> LCLS_FOOD.equals(c.lclsSystm1())).toList(),
                candidates(region, FOOD_TYPE));
        List<PoiCandidate> stays = merge(allTypes.stream().filter(RegionPoiService::isStay).toList(),
                candidates(region, STAY_TYPE));

        RegionPois pois = RegionPois.builder().sights(sights).foods(foods).stays(stays).build();
        log.debug("코스 POI 수집 regionId={} 볼거리={} 맛집={} 숙박={}", regionId, sights.size(), foods.size(), stays.size());
        RegionPois filled = pois.needsSupplement() ? supplement(pois, regionId) : pois;

        // **축제는 보충 판정이 끝난 뒤에 붙인다**(#433). 앞에 붙이면 축제가 볼거리 수에 섞여
        // needsMoreSights() 를 넘겨버려, 국가유산·인허가 보충이 통째로 안 돈다.
        //
        // 실측이 그 대가를 보여준다 — 우리 DB 볼거리가 지역당 평균 82개인데, TourAPI 15개 + 축제
        // 4건이 19개로 "충분" 판정을 받으면 그 82개를 아예 안 쓴다. 축제 4건을 얻고 후보 풀을
        // 97개에서 19개로 줄이는 셈이라, 동선을 고를 여지가 그만큼 사라진다.
        return withOpenFestivals(filled, regionId, travelDate);
    }

    /**
     * TourAPI 가 못 채운 풀을 우리가 가진 데이터로 메운다(#144·#160).
     *
     * <p>TourAPI 숙박은 관광사업체 위주라 지방 숙소가 거의 없다 — 의성군이 1건이다. 그대로 두면 슬롯이 조용히 빠져
     * "잘 곳 없는 2박3일" 이 200 으로 나간다. 보충 데이터엔 사진·소개가 얇으므로 <b>부족할 때만</b> 쓴다.
     *
     * <p><b>볼거리는 국가유산을 먼저 쓴다.</b> 인허가 볼거리에는 야영장·골프장이 섞여 있는데, 국가유산은 그 자체가
     * 관광 자원이고 사진(96%)·설명(98%)까지 온다 — 인허가 장소에는 둘 다 없어 카드가 비었다. 같은 빈자리라면
     * 더 나은 쪽을 먼저 채운다.
     */
    private RegionPois supplement(RegionPois pois, long regionId) {
        // 모자란 풀만 조회한다 — 볼거리만 부족한 지역에서 숙소·맛집까지 끌어올 이유가 없다.
        RegionPois supplemented = pois.supplementedWith(
                pois.needsMoreSights() ? sightCandidates(regionId) : List.of(),
                pois.needsMoreFoods() ? licensedCandidates(regionId, PlaceKind.FOOD) : List.of(),
                pois.needsMoreStays() ? licensedCandidates(regionId, PlaceKind.STAY) : List.of());

        // degrade 한 사실을 남긴다 — 보충이 조용히 일어나면 TourAPI 쪽 공백을 아무도 모른다.
        log.info("국가유산·인허가로 보충 regionId={} 볼거리={}→{} 맛집={}→{} 숙박={}→{}",
                regionId,
                pois.sights().size(), supplemented.sights().size(),
                pois.foods().size(), supplemented.foods().size(),
                pois.stays().size(), supplemented.stays().size());
        return supplemented;
    }

    /**
     * 볼거리 보충 후보 — <b>국가유산 먼저, 그다음 인허가</b>.
     *
     * <p>순서가 곧 우선순위다. 모자란 만큼만 잘라 쓰이므로 앞에 둔 쪽이 먼저 코스에 들어간다.
     */
    private List<PoiCandidate> sightCandidates(long regionId) {
        List<PoiCandidate> heritages = heritagePlaceRepository.findVisitableCandidates(regionId, CANDIDATE_ROWS)
                .stream()
                .map(RegionPoiService::toCandidate)
                .toList();
        // 국가유산으로 이미 다 찼으면 인허가를 읽지 않는다. Stream.concat 은 지연되는 것처럼 보이지만
        // 인자 자리의 licensedCandidates 는 즉시 호출되므로, 그대로 두면 쓰지도 않을 100건을 매 요청
        // 한 번 더 읽는다 — 뒤에서 MIN_SIGHTS 까지만 잘리므로 거의 전부 버려진다.
        if (heritages.size() >= CANDIDATE_ROWS) {
            return heritages;
        }
        return Stream.concat(heritages.stream(), licensedCandidates(regionId, PlaceKind.SIGHT).stream())
                .toList();
    }

    /**
     * 국가유산을 후보로 옮긴다. 방문 대상 판정(대분류)은 저장소 경계에서 끝난다 — 여기서 다시 거르지 않는다.
     *
     * <p>인허가 장소와 달리 <b>사진이 함께 간다.</b> 캐치프레이즈 자리에는 넣지 않는다 — 국가유산청 설명은
     * 한 줄 홍보 문구가 아니라 수백 자짜리 해설이라, 카드에 그대로 흘리면 레이아웃이 무너진다. 그건 상세에서 쓴다.
     */
    private static PoiCandidate toCandidate(HeritagePlace heritage) {
        return PoiCandidate.builder()
                .contentId(heritage.publicId())
                .contentTypeId(NON_TOUR_CONTENT_TYPE)
                .title(heritage.getName())
                .lat(heritage.getLat())
                .lng(heritage.getLng())
                .imageUrl(heritage.getImageUrl())
                .address(heritage.getAddress())
                // 캐치프레이즈는 TourAPI 콘텐츠에만 붙고, 국가유산청은 전화를 주지 않는다.
                // TourAPI 분류체계 밖이라 대분류도 없다 — 이미 볼거리로 정해져 넘어온다.
                .build();
    }

    /**
     * 인허가 장소를 후보로 옮긴다. 정렬·상한은 저장소 경계에서 끝난다 — 관광 콘텐츠성이 높은 분류
     * (한옥·사찰·박물관)가 앞에 오므로, 모자란 만큼만 쓰일 때 더 나은 쪽이 먼저 뽑힌다.
     */
    private List<PoiCandidate> licensedCandidates(long regionId, PlaceKind kind) {
        return licensedPlaceRepository.findCandidates(regionId, kind, CANDIDATE_ROWS).stream()
                .map(RegionPoiService::toCandidate)
                .toList();
    }

    /**
     * 인허가 장소는 TourAPI 콘텐츠가 아니라 상세 조회 대상이 아니다. 식별자에 접두어를 붙여 두 출처를 구분할 수 있게 하고,
     * 콘텐츠 타입은 "TourAPI 아님" 을 뜻하는 0 으로 둔다.
     */
    private static PoiCandidate toCandidate(LicensedPlace place) {
        return PoiCandidate.builder()
                .contentId(place.publicId())
                .contentTypeId(NON_TOUR_CONTENT_TYPE)
                .title(place.getName())
                .lat(place.getLat())
                .lng(place.getLng())
                // 인허가 데이터에는 사진이 없고, 캐치프레이즈도 TourAPI 콘텐츠에만 붙는다.
                .address(place.getAddress())
                .tel(place.getTel()) // 49% 가 채워져 있다 — 있는 것을 버리지 않는다
                // 대분류는 호출부가 kind 로 이미 갈랐다.
                .build();
    }

    /**
     * 한 콘텐츠 타입(또는 {@code null}=전체)의 후보를 좌표 있는 것만 뽑는다.
     *
     * <p><b>TourAPI 실패를 빈 결과로 낮춘다.</b> 예외를 그대로 올리면 인허가 데이터로 채우는 단계까지
     * 가지 못하고 코스 생성이 통째로 502 가 된다 — 실제로 그랬다. 관광 API 일일 한도가 소진되자,
     * DB 에 15만 건이 있는데도 코스가 안 나왔다.
     *
     * <p>외부가 죽어도 코스는 나가야 한다는 것이 장소 풀을 DB 화한 이유다(#144). 다만 조용히 넘기지
     * 않는다 — degrade 한 사실을 warn 으로 남긴다. 후보가 인허가로도 안 채워지면 그때 404 가 나간다.
     */

    /**
     * 그날 안 하는 축제를 뺀다(#388).
     *
     * <h2>왜 필요한가</h2>
     *
     * <p>볼거리 풀은 축제(타입 15)를 포함한다. 그런데 <b>기간이 없어 3월에 끝난 벚꽃축제가 9월 코스에
     * 들어갈 수 있었다</b> — 이름만 보면 그럴듯해서 화면에서도 안 드러나고, 사용자가 현장에 가서야 안다.
     *
     * <h2>모르는 축제는 남긴다</h2>
     *
     * <p>기간을 아는 축제만 뺀다. TourAPI 가 날짜를 안 주는 행이 있고, <b>모르는 것을 끝났다고 단정하면
     * 있는 축제를 우리가 지운다.</b> 없는 것과 "모른다" 는 다르다.
     *
     * <h2>조회는 한 번이다</h2>
     *
     * <p>후보마다 물으면 요청 경로에서 질의가 후보 수만큼 돈다. 축제인 후보의 id 를 모아 <b>한 번에</b>
     * 읽는다 — 축제가 하나도 없으면 질의 자체가 없다.
     */
    private List<PoiCandidate> withoutClosedFestivals(List<PoiCandidate> sights, LocalDate travelDate) {
        List<String> festivalIds = sights.stream()
                .filter(candidate -> candidate.contentTypeId() == FESTIVAL_TYPE)
                .map(PoiCandidate::contentId)
                .filter(contentId -> contentId != null && !contentId.isBlank())
                .toList();
        if (festivalIds.isEmpty()) {
            return sights;
        }

        Map<String, FestivalPeriod> periods = festivalPeriodRepository.findByContentIds(festivalIds);
        List<PoiCandidate> open = sights.stream()
                .filter(candidate -> isOpenOrUnknown(candidate, periods, travelDate))
                .toList();

        int dropped = sights.size() - open.size();
        if (dropped > 0) {
            // degrade 하는 이유를 남긴다 — 후보가 줄어든 것이 데이터 부족인지 날짜 필터인지 구분되게.
            //
            // **여행일은 안 남긴다.** 사용자가 보낸 값이고, 추적 id·사용자 식별자가 같은 줄에 찍히므로
            // 로그를 읽는 사람이 "이 사람이 언제 집을 비우는지" 를 알게 된다. 여기서 필요한 것은
            // 무엇이 줄었나이지 언제 가느냐가 아니다(로깅 규약).
            log.info("그날 안 하는 축제를 뺐습니다 축제후보={} 제외={}건", festivalIds.size(), dropped);
        }
        return open;
    }

    /**
     * <b>그날 열리는 축제를 볼거리 맨 앞에 올린다</b>(#433).
     *
     * <h2>보충 판정이 끝난 뒤에 붙인다</h2>
     *
     * <p><b>순서가 중요하다.</b> 축제를 먼저 붙이면 그 수가 볼거리에 섞여 {@code needsMoreSights()} 를
     * 넘겨버리고, 국가유산·인허가 보충이 통째로 안 돈다 — 축제 몇 건을 얻고 후보 풀 수십 개를 잃는다.
     *
     * <h2>왜 보충이 아니라 항상인가</h2>
     *
     * <p>인허가·국가유산은 TourAPI 가 못 채웠을 때만 쓴다({@code supplement}). 축제는 다르다 —
     * <b>그날만 있는 것</b>이라 볼거리가 넉넉한 지역에서도 넣어야 한다. 보충으로 두면 TourAPI 볼거리가
     * 충분한 안동시에서 안동국제탈춤페스티벌이 코스에 안 들어간다.
     *
     * <p>같은 이유로 <b>맨 앞</b>이다. 국가유산은 다음 달에 가도 그 자리에 있지만 축제는 그 주에만
     * 열린다. 여행 코스라면 그날 갈 수 있는 것을 먼저 넣는 편이 맞다.
     *
     * <h2>여행일을 모르면 넣지 않는다</h2>
     *
     * <p>언제 여는지로 거를 수 없으면 끝난 축제를 코스에 올리게 된다 — #390 이 막으려던 바로 그 일이다.
     *
     * <h2>중복은 이름으로 접고, TourAPI 를 남긴다</h2>
     *
     * <p>TourAPI 축제와 겹칠 수 있다. 89곳에서는 TourAPI 가 1건이라 거의 없지만(#392) 규칙은 둔다.
     * <b>TourAPI 쪽을 남기는 이유</b>는 사진과 개요가 함께 오고 {@code contentId} 로 상세까지 이어지기
     * 때문이다 — 표준데이터에는 사진이 없다.
     *
     * <p>좌표까지 보지 않는다. 축제명은 고유성이 높고("안동국제탈춤페스티벌"), 좌표는 출처마다 정밀도가
     * 달라 같은 축제를 둘로 세기 쉽다.
     */
    private RegionPois withOpenFestivals(RegionPois pois, long regionId, LocalDate travelDate) {
        if (travelDate == null) {
            return pois;
        }
        List<FestivalPlace> open = festivalPlaceRepository.findOpenOn(regionId, travelDate, OPEN_FESTIVAL_LIMIT);
        if (open.isEmpty()) {
            return pois;
        }

        // 보충까지 끝난 볼거리 전체와 견준다 — 국가유산·인허가에도 같은 축제가 있을 수 있다.
        Set<String> existingNames = pois.sights().stream()
                .map(candidate -> normalizedName(candidate.title()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<PoiCandidate> added = open.stream()
                .filter(festival -> !existingNames.contains(normalizedName(festival.getName())))
                .map(RegionPoiService::toCandidate)
                .toList();
        if (added.isEmpty()) {
            return pois;
        }

        // 무엇이 늘었는지 남긴다 — 축제가 코스에 들어간 이유를 나중에 설명할 수 있어야 한다.
        // **여행일은 안 남긴다**(로깅 규약) — 사용자가 언제 집을 비우는지가 로그에 남는다.
        log.info("그날 열리는 축제를 볼거리에 올렸습니다 regionId={} 축제={}건 볼거리={}→{}",
                regionId, added.size(), pois.sights().size(), pois.sights().size() + added.size());
        return RegionPois.builder()
                .sights(Stream.concat(added.stream(), pois.sights().stream()).toList())
                .foods(pois.foods())
                .stays(pois.stays())
                .build();
    }

    /**
     * 이름 비교용 정규화 — <b>공백만 지운다</b>.
     *
     * <p>더 손대지 않는다. 괄호·연도를 떼면 "○○축제 2026" 과 "○○축제 2025" 가 같아져, 다른 회차가
     * 하나로 접힌다.
     */
    private static String normalizedName(String name) {
        return name == null ? null : name.replaceAll("\\s+", "");
    }

    /**
     * 축제를 후보로 옮긴다.
     *
     * <p><b>사진이 없다.</b> 표준데이터는 이미지를 주지 않아 카드가 비는데, 그래도 넣는 이유는 축제가
     * 그날 그 지역에서 <b>실제로 벌어지는 일</b>이라서다. 이름·기간·좌표만으로도 코스에 올릴 값어치가
     * 있고, 사진 없는 카드 문제는 지도 이미지로 메우는 쪽(#394 ④)이 따로 다룬다.
     *
     * <p>설명은 캐치프레이즈 자리에 넣지 않는다 — 지자체가 쓴 글이라 길이가 제각각이고, 카드 한 줄에
     * 흘리면 레이아웃이 무너진다. 국가유산 설명을 뺀 것과 같은 판단이다.
     */
    private static PoiCandidate toCandidate(FestivalPlace festival) {
        return PoiCandidate.builder()
                .contentId(festival.publicId())
                .contentTypeId(NON_TOUR_CONTENT_TYPE)
                .title(festival.getName())
                .lat(festival.getLat())
                .lng(festival.getLng())
                // 표준데이터는 사진을 주지 않는다. 캐치프레이즈·대분류도 TourAPI 콘텐츠 것이다.
                .address(festival.getAddress())
                .tel(festival.getTel())
                .build();
    }

    /**
     * 축제가 아니거나, 기간을 모르거나, 그날 열리면 남긴다.
     *
     * <p><b>여행일을 모르면 축제를 뺀다.</b> 언제 가는지 모르면 그날 여는지도 가릴 수 없어, 남기면
     * 끝난 축제를 코스에 올리게 된다 — #390 이 막으려던 그 일이다. 게다가 기간을 아는 축제는
     * {@code isOpenOn(null)} 에서 터진다.
     *
     * <p>지금은 요청 DTO 가 여행일을 {@code @NotNull} 로 받아 정상 요청으로는 여기에 null 이 안 온다.
     * 그래도 막아 두는 것은 표준데이터 축제 쪽({@code withOpenFestivals})이 같은 가드를 갖고 있어서다 —
     * 한쪽만 있으면 나중에 이 경로가 열렸을 때 두 출처가 다르게 동작한다.
     */
    private static boolean isOpenOrUnknown(
            PoiCandidate candidate, Map<String, FestivalPeriod> periods, LocalDate travelDate) {
        if (candidate.contentTypeId() != FESTIVAL_TYPE) {
            return true;
        }
        if (travelDate == null) {
            return false;
        }
        FestivalPeriod period = periods.get(candidate.contentId());
        return period == null || period.isOpenOn(travelDate);
    }

    private List<PoiCandidate> candidates(Region region, Integer contentTypeId) {
        TourPoiResult result;
        try {
            result = tourApiClient.findByArea(
                    region.getAreaCode(), region.getSigunguCode(), contentTypeId, CANDIDATE_ROWS);
        } catch (RuntimeException e) {
            log.warn("TourAPI 조회 실패 — 인허가 데이터로 대체합니다. regionId={} contentTypeId={} cause={}",
                    region.getId(), contentTypeId, e.getClass().getSimpleName());
            return List.of();
        }

        List<PoiCandidate> out = new ArrayList<>();
        for (TourPoi poi : result.items()) {
            PoiCandidate candidate = toCandidate(poi);
            if (candidate != null) {
                out.add(candidate); // 좌표·필수값 결여는 제외
            }
        }
        return out;
    }

    /**
     * 볼거리인가 — <b>숙박·맛집이 아닌 것</b>.
     *
     * <p>대분류가 있으면 그걸 믿는다(TourAPI 출처). 없으면({@code null}) 우리 DB 출처라 이미 볼거리로
     * 정해져 넘어온 것이고, 그때만 {@code contentTypeId} 로 되돌아간다.
     */
    private static boolean isSight(PoiCandidate candidate) {
        String lcls = candidate.lclsSystm1();
        if (lcls == null) {
            return SIGHT_TYPES.contains(candidate.contentTypeId());
        }
        return !isStay(candidate) && !LCLS_FOOD.equals(lcls);
    }

    /**
     * 잘 곳인가 — <b>대분류가 숙박이거나, 중분류가 리조트</b>다.
     *
     * <p>리조트를 따로 보는 이유는 그것만 대분류가 어긋나서다({@code VE} 문화관광). 나머지는 대분류로 갈린다.
     */
    private static boolean isStay(PoiCandidate candidate) {
        return LCLS_STAY.equals(candidate.lclsSystm1()) || LCLS_RESORT.equals(candidate.lclsSystm2());
    }

    /**
     * 두 조회 결과를 합친다 — <b>{@code contentId} 로 중복을 접는다</b>.
     *
     * <p>전체타입 조회와 타입별 조회가 같은 장소를 함께 물고 온다. 접지 않으면 같은 숙소가 코스에
     * 두 번 들어갈 수 있다.
     */
    private static List<PoiCandidate> merge(List<PoiCandidate> first, List<PoiCandidate> second) {
        Map<String, PoiCandidate> byContentId = new LinkedHashMap<>();
        Stream.concat(first.stream(), second.stream())
                .forEach(candidate -> byContentId.putIfAbsent(candidate.contentId(), candidate));
        return List.copyOf(byContentId.values());
    }

    private PoiCandidate toCandidate(TourPoi poi) {
        if (poi.contentTypeId() == null || poi.contentId() == null || poi.title() == null
                || poi.lat() == null || poi.lng() == null) {
            return null;
        }
        // 추천 한 줄(catchphrase)·주소는 코스 슬롯을 트리플식으로 인라인 렌더하기 위한 표시 정보다.
        return PoiCandidate.builder()
                .contentId(poi.contentId())
                .contentTypeId(poi.contentTypeId())
                .title(poi.title())
                .lat(poi.lat())
                .lng(poi.lng())
                .imageUrl(poi.firstImage())
                .address(poi.address())
                .catchphrase(catchphraseProvider.forContentId(poi.contentId()).orElse(null))
                // 후보 조회 응답에 이미 들어 있다. 여기서 안 들고 가면 상세를 다시 불러야 얻는다.
                .tel(poi.tel())
                .lclsSystm1(poi.lclsSystm1())
                .lclsSystm2(poi.lclsSystm2())
                .build();
    }
}
