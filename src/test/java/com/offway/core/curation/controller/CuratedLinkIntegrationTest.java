package com.offway.core.curation.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.curation.domain.Surface;
import com.offway.core.curation.repository.CuratedLinkJpaRepository;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.HeritageGroup;
import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.repository.HeritagePlaceRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 큐레이션 링크가 앱 네 면에 나가는 HTTP 계약(#341).
 *
 * <p>여기서 잠그는 것은 <b>"안 나가야 할 것이 안 나가는가"</b> 와 <b>"나가는 것의 모양이 계약대로인가"</b> 다.
 * 면이 갈리는 규칙 자체는 {@code CuratedLinkTest} 가 전수로 돈다 — 통합은 지역 상세 하나를 대표로 본다.
 *
 * <p><b>목록 크기로 단언하지 않는다.</b> {@code R__seed_curated_links.sql} 이 넣은 상설 링크가 늘 함께
 * 실려 있어, 개수를 박으면 seed 를 한 줄 고칠 때마다 무관한 테스트가 깨진다. 대신 이 테스트가 심은
 * 링크의 <b>있음·없음</b>만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CuratedLinkIntegrationTest {

    private static final String REGION_URL = "/api/v1/regions/{regionId}";
    private static final String HOME_URL = "/api/v1/home";
    private static final String POI_URL = "/api/v1/pois/{contentId}";
    private static final String COURSES_URL = "/api/v1/courses";
    private static final String COURSE_URL = "/api/v1/courses/{courseId}";

    private static final String LINK_TITLES = "$.data.curatedLinks[*].title";

    /**
     * <b>서비스와 같은 시간대로 오늘을 잡는다.</b> {@code CurationService} 는 {@code Asia/Seoul} 로 기간을
     * 판정하는데, 여기서 시스템 기본 시간대로 "어제" 를 만들면 두 시간대의 날짜가 갈리는 시간대(KST 오전
     * 09시 이전)에 그 "어제" 가 서비스 기준으로는 아직 오늘이라 만료 링크가 살아 있게 된다. 그러면 테스트가
     * 하루 중 몇 시간에만 깨진다.
     */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 정선(16) 당일치기 — 코스 상세를 열려면 저장된 코스가 하나 필요하다. */
    private static final String COURSE_BODY =
            """
            { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0},
                {"order":2,"timeOfDay":"LUNCH","kind":"FOOD","poiContentId":"c2","title":"맛집1","lat":37.51,"lng":128.61,"travelMinutes":15}
              ]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuratedLinkJpaRepository curatedLinkJpaRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private HeritagePlaceRepository heritagePlaceRepository;

    // ── 계약 ──────────────────────────────────────────────────────────────

    @Test
    void 지역_상세에_켜진_링크가_계약대로_실린다() throws Exception {
        save(link("전남 관광포털", "축제 보러 가기", "이번 달 축제를 모아 놨다", Set.of(Surface.REGION), true));

        mockMvc.perform(get(REGION_URL, anyRegionId()).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath(LINK_TITLES, hasItem("전남 관광포털")))
                .andExpect(jsonPath("$.data.curatedLinks[?(@.title=='전남 관광포털')].chipText", hasItem("축제 보러 가기")))
                .andExpect(jsonPath(
                        "$.data.curatedLinks[?(@.title=='전남 관광포털')].description", hasItem("이번 달 축제를 모아 놨다")))
                .andExpect(jsonPath(
                        "$.data.curatedLinks[?(@.title=='전남 관광포털')].linkUrl", hasItem("https://tour.jeonnam.go.kr")))
                // 링크 주소는 언제나 https 다 — 도메인이 저장 시점에 막는다.
                .andExpect(jsonPath("$.data.curatedLinks[*].linkUrl", everyItem(org.hamcrest.Matchers.startsWith("https://"))));
    }

    /**
     * <b>없는 값은 키가 빠지는 게 아니라 {@code null} 로 실린다.</b> 이 레포는 필드를 빼지 않으므로 앱은
     * {@code null} 을 보고 그 줄을 접는다. {@code doesNotExist()} 가 아니라 {@code nullValue()} 다.
     */
    @Test
    void 부제와_썸네일이_없으면_키가_사라지지_않고_null_로_실린다() throws Exception {
        save(link("코레일", "기차표 예매", null, Set.of(Surface.REGION), true));

        mockMvc.perform(get(REGION_URL, anyRegionId()).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedLinks[?(@.title=='코레일')].description", hasItem(nullValue())))
                .andExpect(jsonPath("$.data.curatedLinks[?(@.title=='코레일')].thumbnailUrl", hasItem(nullValue())));
    }

    // ── 안 나가야 할 것 ────────────────────────────────────────────────────

    /** 어드민이 만들다 만 항목이 곧바로 사용자에게 보이면 안 된다. */
    @Test
    void 게시하지_않은_링크는_실리지_않는다() throws Exception {
        save(link("작성 중인 배너", "아직", null, Set.of(Surface.REGION), false));

        mockMvc.perform(get(REGION_URL, anyRegionId()).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LINK_TITLES, not(hasItem("작성 중인 배너"))));
    }

    /** 기간이 지난 것을 계속 내리면 눌러 들어가도 끝난 행사다 — policy 가 덴 자리다(#217). */
    @Test
    void 기간이_지난_링크는_실리지_않는다() throws Exception {
        LocalDate yesterday = LocalDate.now(SERVICE_ZONE).minusDays(1);
        save(CuratedLink.builder()
                .title("끝난 축제")
                .chipText("지난 행사")
                .linkUrl("https://festival.example")
                .startsOn(yesterday.minusDays(30))
                .endsOn(yesterday)
                .surfaces(Set.of(Surface.REGION))
                .published(true)
                .build());

        mockMvc.perform(get(REGION_URL, anyRegionId()).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LINK_TITLES, not(hasItem("끝난 축제"))));
    }

    @Test
    void 아직_시작하지_않은_링크는_실리지_않는다() throws Exception {
        LocalDate tomorrow = LocalDate.now(SERVICE_ZONE).plusDays(1);
        save(CuratedLink.builder()
                .title("다음 달 축제")
                .chipText("곧 시작")
                .linkUrl("https://festival.example")
                .startsOn(tomorrow)
                .endsOn(tomorrow.plusDays(30))
                .surfaces(Set.of(Surface.REGION))
                .published(true)
                .build());

        mockMvc.perform(get(REGION_URL, anyRegionId()).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LINK_TITLES, not(hasItem("다음 달 축제"))));
    }

    /** 면을 나눈 이유가 이것이다 — 홈 배너와 지역 상세에 똑같은 목록이 다 뜨면 화면이 지저분해진다. */
    @Test
    void 그_면이_켜지지_않은_링크는_실리지_않는다() throws Exception {
        save(link("코스에서만 보일 것", "코스 전용", null, Set.of(Surface.COURSE), true));

        mockMvc.perform(get(REGION_URL, anyRegionId()).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LINK_TITLES, not(hasItem("코스에서만 보일 것"))));
    }

    // ── 나머지 세 면 ──────────────────────────────────────────────────────

    @Test
    void 홈에도_같은_모양으로_실린다() throws Exception {
        save(link("홈 배너", "홈에서 보기", null, Set.of(Surface.HOME), true));

        mockMvc.perform(get(HOME_URL).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LINK_TITLES, hasItem("홈 배너")))
                .andExpect(jsonPath(LINK_TITLES, not(hasItem("코스 전용 배너"))));
    }

    @Test
    void 장소_상세에도_같은_모양으로_실린다() throws Exception {
        save(link("장소 배너", "장소에서 보기", null, Set.of(Surface.POI), true));

        mockMvc.perform(get(POI_URL, anyHeritagePublicId()).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LINK_TITLES, hasItem("장소 배너")));
    }

    @Test
    void 코스_상세에도_같은_모양으로_실린다() throws Exception {
        save(link("코스 배너", "코스에서 보기", null, Set.of(Surface.COURSE), true));
        UUID userId = UUID.randomUUID();

        String saved = mockMvc.perform(post(COURSES_URL)
                        .with(loginAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COURSE_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long courseId = ((Number) JsonPath.read(saved, "$.data.courseId")).longValue();

        mockMvc.perform(get(COURSE_URL, courseId).with(loginAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LINK_TITLES, hasItem("코스 배너")))
                .andExpect(jsonPath(LINK_TITLES, not(hasItem("홈 배너"))));
    }

    // ── fixture ──────────────────────────────────────────────────────────

    private void save(CuratedLink link) {
        curatedLinkJpaRepository.save(link);
    }

    /** 기간 판정과 무관하게 늘 보이는 링크 — 각 테스트가 자기가 볼 조건만 바꿔 만든다. */
    private static CuratedLink link(
            String title, String chipText, String description, Set<Surface> surfaces, boolean published) {
        return CuratedLink.builder()
                .title(title)
                .chipText(chipText)
                .description(description)
                .linkUrl("https://tour.jeonnam.go.kr")
                .alwaysOn(true)
                .surfaces(surfaces)
                .published(published)
                .build();
    }

    /** 어느 지역이든 좋다 — 이 테스트가 보는 것은 지역 내용이 아니라 함께 실리는 링크다. */
    private long anyRegionId() {
        List<Region> regions = regionRepository.findAll();
        if (regions.isEmpty()) {
            throw new IllegalStateException("지역 마스터가 비어 있습니다 — 마이그레이션 seed 를 확인하세요");
        }
        return regions.getFirst().getId();
    }

    /**
     * 국가유산 장소를 하나 심고 그 공개 ID 를 준다.
     *
     * <p>{@code HER-} 접두어는 장소 상세가 <b>DB 만 읽는</b> 경로다. TourAPI 콘텐츠 ID 로 부르면 외부 호출이
     * 껴서 이 테스트가 네트워크에 매달린다.
     */
    private String anyHeritagePublicId() {
        long regionId = anyRegionId();
        heritagePlaceRepository.saveAll(List.of(HeritagePlace.builder()
                .regionId(regionId)
                .kind("보물")
                .group(HeritageGroup.HISTORIC_STRUCTURE)
                .name("정공단")
                .address("부산광역시 동구")
                .lat(35.13)
                .lng(129.05)
                .build()));
        return heritagePlaceRepository.findVisitableCandidates(regionId, 1).stream()
                .findFirst()
                .map(HeritagePlace::publicId)
                .orElseThrow(() -> new IllegalStateException("국가유산 적재가 실패했습니다"));
    }
}
