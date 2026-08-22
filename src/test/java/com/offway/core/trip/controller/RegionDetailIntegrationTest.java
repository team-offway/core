package com.offway.core.trip.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionPoi;
import com.offway.core.trip.repository.RegionPoiRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지역 상세 API 의 HTTP 계약(#304).
 *
 * <p>이 화면은 지금 회색 판으로 남아 있다 — 앱이 쓰는 인허가 목록에 사진·소개 필드가 아예 없기 때문이다.
 * 여기서 잠그는 것은 <b>"사진 있는 장소만 나가는가"</b> 와 <b>"없는 지역이 404 인가"</b> 다.
 *
 * <p><b>숫자가 아닌 지역 ID 는 안 본다(의도적 생략).</b> 경로 변수 타입 불일치는 프레임워크가 판정하는
 * 400 이라 우리 계약이 아니다 — 규약이 문서화 대상에서도 빼는 자리다.
 *
 * <p><b>장소는 리포지토리로 직접 심는다.</b> 배치를 태우면 외부 stub 구성이 달라져 컨텍스트가 하나 더 뜨고,
 * 이 테스트가 보려는 것은 적재가 아니라 응답 모양이다. 적재 쪽은 {@code RegionPoiRefreshIntegrationTest} 가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class RegionDetailIntegrationTest {

    private static final String URL = "/api/v1/regions/{regionId}";

    private static final YearMonth BASE_YM = YearMonth.of(2099, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegionPoiRepository regionPoiRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void 지역_이름과_매력_포인트_장소를_함께_준다() throws Exception {
        long regionId = anyRegionId();
        regionPoiRepository.replaceRegion(regionId, List.of(
                poi(regionId, "C-100", "범일 이중섭거리", "http://img/100.jpg", Category.SIGHT)));

        mockMvc.perform(get(URL, regionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.regionId").value(regionId))
                // 표기는 "시군구 · 시도" — 홈·추천 응답과 같은 모양이어야 앱이 화면마다 다르게 안 그린다.
                .andExpect(jsonPath("$.data.name").value(org.hamcrest.Matchers.containsString(" · ")))
                .andExpect(jsonPath("$.data.highlightSpots.length()").value(1))
                .andExpect(jsonPath("$.data.highlightSpots[0].poiContentId").value("C-100"))
                .andExpect(jsonPath("$.data.highlightSpots[0].name").value("범일 이중섭거리"))
                .andExpect(jsonPath("$.data.highlightSpots[0].imageUrl").value("http://img/100.jpg"))
                // 사진이 없으면 화면이 회색 판이 된다. 이 필드가 이 API 의 존재 이유다.
                .andExpect(jsonPath("$.data.photos").isArray());
    }

    /**
     * <b>사진 없는 장소는 나가지 않는다.</b>
     *
     * <p>프론트가 명시적으로 요청한 조건이다 — 섞이면 가로 목록 중간중간에 회색 판이 껴서 더 이상해 보인다.
     */
    @Test
    void 사진_없는_장소는_내려가지_않는다() throws Exception {
        long regionId = anyRegionId();
        regionPoiRepository.replaceRegion(regionId, List.of(
                poi(regionId, "C-200", "사진 있는 곳", "http://img/200.jpg", Category.SIGHT),
                poi(regionId, "C-201", "사진 없는 곳", null, Category.SIGHT),
                poi(regionId, "C-202", "사진이 빈 곳", "", Category.FOOD)));

        mockMvc.perform(get(URL, regionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highlightSpots.length()").value(1))
                .andExpect(jsonPath("$.data.highlightSpots[0].poiContentId").value("C-200"));
    }

    /**
     * 최대 10개다 — 시안 노트가 정한 수.
     *
     * <p>상한을 조회에서 거는 이유는 세는 쪽과 내리는 쪽을 같게 두기 위해서다. 앱에서 자르면 "10개를
     * 달라" 고 했는데 사진 없는 것이 섞여 3개만 그려지는 일이 생긴다.
     */
    @Test
    void 매력_포인트_장소는_최대_열_개다() throws Exception {
        long regionId = anyRegionId();
        List<RegionPoi> many = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> poi(regionId, "C-3%02d".formatted(i), "장소" + i, "http://img/3%02d.jpg".formatted(i), Category.SIGHT))
                .toList();
        regionPoiRepository.replaceRegion(regionId, many);

        mockMvc.perform(get(URL, regionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highlightSpots.length()").value(10));
    }

    /**
     * 장소가 없어도 200 이다 — 화면의 나머지(소개·사진·혜택)는 그려야 한다.
     *
     * <p>404 로 끊으면 적재가 아직 안 된 지역에서 지역 상세가 통째로 안 열린다.
     */
    @Test
    void 장소가_없어도_200이고_빈_목록이다() throws Exception {
        long regionId = anyRegionId();
        regionPoiRepository.replaceRegion(regionId, List.of());

        mockMvc.perform(get(URL, regionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highlightSpots").isArray())
                .andExpect(jsonPath("$.data.highlightSpots.length()").value(0));
    }

    /** 없는 지역은 404 다. 지역 마스터가 89곳이라 옛 링크로 정상 요청이 여기 닿을 수 있다. */
    @Test
    void 없는_지역은_404_TRIP_002_다() throws Exception {
        mockMvc.perform(get(URL, 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TRIP-002"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private long anyRegionId() {
        List<Region> regions = new ArrayList<>(regionRepository.findAll());
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.get(regions.size() - 1).getId();
    }

    private static RegionPoi poi(long regionId, String contentId, String title, String image, Category category) {
        return RegionPoi.builder()
                .regionId(regionId)
                .contentId(contentId)
                .contentTypeId(12)
                .category(category)
                .title(title)
                .imageUrl(image)
                .address("주소")
                .baseYm(BASE_YM)
                .fetchedAt(LocalDateTime.of(2099, 1, 1, 4, 0))
                .build();
    }
}
