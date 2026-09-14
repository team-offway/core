package com.offway.core.trip.infrastructure.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.NoOpCallRecorder;
import com.offway.core.trip.infrastructure.pet.dto.PetTourDetail;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * {@code detailPetTour2} 응답 파싱 — <b>물어본 그 장소의 조건인지</b>를 여기서 가른다.
 *
 * <p>동반 조건은 사용자가 그걸 믿고 반려동물을 데려가는 값이다. 다른 장소의 조건이 붙으면
 * "전구역 동반가능" 을 보고 갔다가 못 들어간다 — 예외도 로그도 없이 조용히 틀린다.
 */
class PetTourClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);

    private static final String ASKED = "127311";
    private static final Duration WAIT = Duration.ofSeconds(5);

    private static PetTourClient client(String body) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        ExchangeFunction stub = request -> Mono.just(response);
        return new PetTourClientImpl(
                WebClient.builder().exchangeFunction(stub).build(), WITH_KEY, new NoOpCallRecorder());
    }

    /** 목록은 소문자(contentid), 상세는 카멜케이스(acmpyTypeCd)다 — 한쪽으로 통일해 읽으면 0건이 된다. */
    @Test
    void 물어본_장소의_동반_조건을_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"contentid":"127311","acmpyTypeCd":"전구역 동반가능",
                   "acmpyPsblCpam":"제한 없음","acmpyNeedMtr":"목줄 착용",
                   "etcAcmpyInfo":"해변 산책 가능","relaAcdntRiskMtr":"해루질 구역 주의",
                   "relaPosesFclty":"","relaFrnshPrdlst":"","relaRntlPrdlst":""}
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}""";

        Optional<PetTourDetail> found = client(body).findDetail(ASKED, WAIT);

        assertTrue(found.isPresent());
        assertEquals(ASKED, found.get().contentId());
        assertEquals("전구역 동반가능", found.get().accompanyArea());
        assertEquals("목줄 착용", found.get().requiredMatter());
    }

    /**
     * <b>순서를 믿지 않는다.</b> 다른 장소가 앞에 와도 물어본 쪽을 골라야 한다.
     *
     * <p>첫 항목을 그대로 쓰면 "전구역" 인 곳에 "일부구역 · 5kg 이내" 가 붙는다.
     */
    @Test
    void 다른_장소가_앞에_와도_물어본_장소를_고른다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"contentid":"999999","acmpyTypeCd":"일부구역 동반가능",
                   "acmpyPsblCpam":"5kg 이내 소형견"},
                  {"contentid":"127311","acmpyTypeCd":"전구역 동반가능",
                   "acmpyPsblCpam":"제한 없음"}
                ]},"numOfRows":2,"pageNo":1,"totalCount":2}}}""";

        Optional<PetTourDetail> found = client(body).findDetail(ASKED, WAIT);

        assertTrue(found.isPresent());
        assertEquals(ASKED, found.get().contentId());
        assertEquals("전구역 동반가능", found.get().accompanyArea(),
                "앞 항목을 집으면 남의 조건이 이 장소에 붙는다");
    }

    /**
     * <b>물어본 장소가 없으면 비운다.</b> 아무거나 집는 것보다 모르는 편이 낫다 — 호출자가 unknown 으로
     * 채워 칩은 뜨고 열 내용만 빈다.
     */
    @Test
    void 물어본_장소가_응답에_없으면_비운다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"contentid":"999999","acmpyTypeCd":"일부구역 동반가능"}
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}""";

        assertTrue(client(body).findDetail(ASKED, WAIT).isEmpty());
    }

    /** 결과 없음은 items 가 <b>빈 문자열</b>로 온다 — 배열로 다루면 터진다. */
    @Test
    void 결과가_없으면_비운다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}""";

        assertTrue(client(body).findDetail(ASKED, WAIT).isEmpty());
    }

    /** 한 건이면 item 이 배열이 아니라 단일 객체다. */
    @Test
    void 단일_객체로_와도_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":
                  {"contentid":"127311","acmpyTypeCd":"전구역 동반가능"}
                },"numOfRows":1,"pageNo":1,"totalCount":1}}}""";

        Optional<PetTourDetail> found = client(body).findDetail(ASKED, WAIT);

        assertTrue(found.isPresent());
        assertEquals("전구역 동반가능", found.get().accompanyArea());
    }
}
