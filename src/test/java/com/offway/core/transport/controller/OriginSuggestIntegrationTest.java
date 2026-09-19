package com.offway.core.transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.BusTerminal;
import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.domain.TrainStation;
import com.offway.core.transport.infrastructure.kakao.OriginPlaceSearchClient;
import com.offway.core.transport.infrastructure.kakao.StubOriginPlaceSearchClient;
import com.offway.core.transport.infrastructure.kakao.dto.FoundPlace;
import com.offway.core.transport.repository.BusTerminalJpaRepository;
import com.offway.core.transport.repository.TrainStationJpaRepository;
import com.offway.core.transport.service.OriginHubCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출발지 자동완성 HTTP 계약(#590).
 *
 * <p>시드가 이미 전국 허브를 담고 있으므로 <b>있는 데이터로 검증한다</b> — 마이그레이션이 채운 시도가
 * 실제로 검색에 걸리는지가 이 기능의 핵심이라, 픽스처로 갈아끼우면 그것을 확인하지 못한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class OriginSuggestIntegrationTest {

    private static final String URL = "/api/v1/origins";

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        OriginPlaceSearchClient stubOriginPlaceSearchClient() {
            return new StubOriginPlaceSearchClient();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OriginPlaceSearchClient placeSearchClient;

    @Autowired
    private OriginHubCatalog catalog;

    @Autowired
    private TrainStationJpaRepository stationJpaRepository;

    @Autowired
    private BusTerminalJpaRepository terminalJpaRepository;

    private StubOriginPlaceSearchClient stub() {
        return (StubOriginPlaceSearchClient) placeSearchClient;
    }

    @Test
    void 서울을_치면_이름에_서울이_없는_서울_역들도_함께_온다() throws Exception {
        stub().willReturnNothing();

        mockMvc.perform(get(URL).param("query", "서울"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                // 이름이 '서울' 로 시작하는 것이 먼저고, 그중 짧은 이름이 대표다
                .andExpect(jsonPath("$.data[0].name").value("서울역"))
                .andExpect(jsonPath("$.data[1].name").value("서울남부터미널"))
                // 부르는 이름이 있는 대표 허브가 그 뒤 — 원본 이름(서울경부)으로 걸린다
                .andExpect(jsonPath("$.data[2].name").value("고속버스터미널(경부·영동)"))
                // 지역으로 걸린 것들 — 이름 매칭만으로는 안 나오는 역들이 이 API 의 이유다
                .andExpect(jsonPath("$.data[?(@.name == '청량리역')]").exists())
                .andExpect(jsonPath("$.data[?(@.name == '용산역')]").exists())
                .andExpect(jsonPath("$.data[?(@.area == '서울')]").exists());
    }

    @Test
    void 부르는_이름으로도_찾힌다() throws Exception {
        stub().willReturnNothing();

        mockMvc.perform(get(URL).param("query", "고속버스터미널"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '고속버스터미널(경부·영동)')]").exists())
                .andExpect(jsonPath("$.data[0].kind").value("BUS_TERMINAL"));
    }

    @Test
    void 두_글자_미만이면_빈_배열이고_외부를_부르지_않는다() throws Exception {
        // **stub 을 일부러 던지게 둔다.** 외부를 불렀다면 이 테스트가 500 으로 깨진다 — 세팅을
        // 생략하면 앞 테스트가 남긴 응답이 살아남아 조용히 통과한다(실제로 그렇게 통과했다).
        stub().willThrow();
        mockMvc.perform(get(URL).param("query", "서"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 검색어가_없어도_400_이_아니다() throws Exception {
        stub().willThrow();
        // 입력창이 비어 있는 상태로 화면이 열린다 — 그때 400 이면 화면이 열리는 순간 오류가 뜬다.
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 허브가_없는_말은_주소_검색이_메운다() throws Exception {
        stub().willReturn(List.of(
                new FoundPlace("분당구청", "경기 성남시 분당구 야탑로 50", new Coordinate(37.4200, 127.1265))));

        mockMvc.perform(get(URL).param("query", "분당구청"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.kind == 'ADDRESS')].name").value("분당구청"))
                // 좌표 코드라 앱은 여전히 문자열 하나만 들고 다닌다
                .andExpect(jsonPath("$.data[?(@.kind == 'ADDRESS')].code")
                        .value("GEO:37.42,127.1265"));
    }

    @Test
    void 경유_정류소는_제안에_오르지_않는다() throws Exception {
        stub().willReturnNothing();
        // 잠실역·독바위는 시드에서 is_terminal=0 이다. 특정 노선만 서므로 "거기서 타세요" 가 틀린다.
        mockMvc.perform(get(URL).param("query", "잠실"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '잠실역터미널')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.name == '잠실역')]").doesNotExist());
    }

    @Test
    void 좌표가_없는_허브는_제안에_오르지_않는다() throws Exception {
        stub().willReturnNothing();
        stationJpaRepository.save(TrainStation.of("TESTNOXY", "좌표없는테스트역", null, null, "서울특별시"));
        terminalJpaRepository.save(
                BusTerminal.of("TESTNOXY2", "좌표없는테스트터미널", BusTerminalKind.EXPRESS, null, null, "서울특별시"));
        catalog.evictCache();

        mockMvc.perform(get(URL).param("query", "좌표없는테스트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        catalog.evictCache();
    }

    @Test
    void 같은_지점에_코드가_여럿이면_한_줄로_접힌다() throws Exception {
        stub().willReturnNothing();
        // 동서울은 시드에 5건(고속 4·시외 1)이고 좌표가 전부 같다.
        mockMvc.perform(get(URL).param("query", "동서울"))
                .andExpect(status().isOk())
                // 다섯 코드가 한 줄로 접혀 결과가 하나다. **필터 결과에 length() 를 쓰지 않는다** —
                // JsonPath 가 걸린 객체의 필드 수(4)를 세어 조용히 통과하거나 조용히 깨진다.
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("동서울터미널"));
    }
}
