package com.offway.core.trip.controller.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.response.DataSource;
import com.offway.core.trip.controller.dto.RegionDetailResponse.HighlightSpot;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 지역 상세가 <b>실제로 실린 출처만</b> 밝히는지(#399).
 *
 * <p>이 화면은 출처가 섞이는 대표적인 자리다 — 소개·대표 사진은 공사 것이고, 매력 포인트에는 인허가·
 * 국가유산 장소가 함께 온다. 여기서 하나를 빠뜨리면 화면이 <b>안 쓴 출처를 표기하거나 쓴 출처를 빠뜨린다.</b>
 */
class RegionDetailResponseSourcesTest {

    private static HighlightSpot 장소(String id, String catchphrase) {
        return new HighlightSpot(id, "이름", "http://img/1.jpg", catchphrase);
    }

    private static final RegionVisitMetricsResponse 지표없음 = new RegionVisitMetricsResponse(null, null);

    private static RegionDetailResponse 지역상세(String overview, List<String> photos, List<HighlightSpot> spots) {
        return 지역상세(overview, photos, spots, 지표없음);
    }

    private static RegionDetailResponse 지역상세(
            String overview, List<String> photos, List<HighlightSpot> spots,
            RegionVisitMetricsResponse metrics) {
        return new RegionDetailResponse(
                1L, "동구 · 부산광역시", overview, photos, null, spots, List.of(), metrics);
    }

    @Test
    void 소개와_사진이_있으면_공사가_붙는다() {
        RegionDetailResponse 응답 = 지역상세("금수사가 있는 곳", List.of("http://img/hero.jpg"), List.of());

        assertEquals(Set.of(DataSource.KTO), 응답.sources());
    }

    @Test
    void 장소의_출처가_섞이면_모두_실린다() {
        RegionDetailResponse 응답 = 지역상세(null, List.of(),
                List.of(장소("C-100", null), 장소("LIC-4242", null), 장소("HER-1234", null)));

        assertEquals(
                Set.of(DataSource.KTO, DataSource.LOCAL_PERMIT, DataSource.KHS),
                응답.sources());
    }

    @Test
    void 인허가_장소만_있으면_국가유산청은_안_붙는다() {
        // 안 쓴 출처를 표기하는 것도 잘못된 표기다.
        RegionDetailResponse 응답 = 지역상세(null, List.of(), List.of(장소("LIC-1", null), 장소("LIC-2", null)));

        assertEquals(Set.of(DataSource.LOCAL_PERMIT), 응답.sources());
    }

    /**
     * <b>캐치프레이즈만 있어도 공사가 붙는다.</b>
     *
     * <p>인허가 장소에도 붙는 값인데 그건 공사의 "대한민국 구석구석" 에서 온다. 소개도 대표 사진도 없는
     * 지역에서 그 한 줄만 실리면, 장소의 기본 출처만 세다가 공사 표기를 통째로 빠뜨린다.
     */
    @Test
    void 캐치프레이즈만_있어도_공사가_붙는다() {
        RegionDetailResponse 응답 = 지역상세(null, List.of(), List.of(장소("LIC-4242", "한옥에서 하룻밤")));

        assertEquals(Set.of(DataSource.LOCAL_PERMIT, DataSource.KTO), 응답.sources());
    }

    @Test
    void 우리_값만_실린_지역은_표기할_출처가_없다() {
        assertTrue(지역상세(null, List.of(), List.of()).sources().isEmpty());
    }

    /**
     * <b>방문 지표만 있어도 공사가 붙는다.</b>
     *
     * <p>한산한 요일과 인기 추세는 관광빅데이터에서 온 값이다. 소개도 대표 사진도 매력 포인트도 없는
     * 지역에서 지표만 실리면, 실제로 공사 데이터를 쓰고도 표기가 통째로 빠진다.
     */
    @Test
    void 방문_지표만_있어도_공사가_붙는다() {
        RegionVisitMetricsResponse 지표 = new RegionVisitMetricsResponse(
                new RegionVisitMetricsResponse.QuietestDayResponse(DayOfWeek.TUESDAY, "화요일", 30), null);

        RegionDetailResponse 응답 = 지역상세(null, List.of(), List.of(), 지표);

        assertEquals(Set.of(DataSource.KTO), 응답.sources());
    }

    @Test
    void 인기_추세만_있어도_공사가_붙는다() {
        RegionVisitMetricsResponse 지표 = new RegionVisitMetricsResponse(
                null, new RegionVisitMetricsResponse.TrendResponse(40, true));

        assertEquals(Set.of(DataSource.KTO), 지역상세(null, List.of(), List.of(), 지표).sources());
    }
}
