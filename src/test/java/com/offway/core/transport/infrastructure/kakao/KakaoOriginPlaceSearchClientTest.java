package com.offway.core.transport.infrastructure.kakao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.external.ExternalKeyState;
import com.offway.core.transport.infrastructure.kakao.dto.FoundPlace;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 카카오 로컬로 <b>실제로 나가는 요청</b>(#599).
 *
 * <p>여기서 잠그는 것은 하나다 — <b>사용자가 친 말이 그대로 카카오에 닿는가.</b>
 *
 * <h2>왜 이 테스트가 없어서 12일을 못 봤나</h2>
 *
 * <p>이 어댑터는 실패를 전부 <b>빈 목록</b>으로 바꾼다(그건 의도다 — 출발지 검색이 실패해도 역·터미널로
 * 답해야 한다). 그래서 요청이 망가져 있어도 응답은 {@code 200 + []} 이고, <b>"검색 결과가 없다" 와
 * 구분이 안 된다.</b> 운영에서 한글 검색이 통째로 죽어 있었는데 아무도 몰랐던 이유가 이것이다.
 *
 * <p>응답을 보는 테스트로는 이걸 못 잡는다. <b>나가는 요청</b>을 봐야 한다.
 */
class KakaoOriginPlaceSearchClientTest {

    /** 외부 경계 stub — 나가는 요청을 붙잡고, 응답은 빈 결과로 돌려준다. */
    private final List<URI> sent = new ArrayList<>();

    private KakaoOriginPlaceSearchClient client(String restApiKey) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    sent.add(request.url());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"documents\":[]}")
                            .build());
                })
                .build();
        // repository 가 null 이어도 record 가 삼킨다 — 여기서 보려는 것은 URI 뿐이다.
        return new KakaoOriginPlaceSearchClient(
                webClient,
                ExternalApiProperties.ofKakao(restApiKey),
                new ExternalApiCallRecorder(null, message -> { }, new ExternalKeyState()));
    }

    /**
     * <b>한글이 한 번만 인코딩돼야 한다.</b>
     *
     * <p>{@code toUriString()} 으로 만든 <b>이미 인코딩된 문자열</b>을 {@code uri(String)} 에 주면
     * WebClient 가 그것을 URI 템플릿으로 보고 한 번 더 인코딩한다 — {@code %EC} 가 {@code %25EC} 가
     * 되어, 카카오는 {@code 서울} 이 아니라 <b>{@code "%EC%84%9C%EC%9A%B8"} 이라는 글자</b>를 받는다.
     */
    @Test
    void 한글_검색어가_그대로_카카오에_닿는다() {
        client("test-key").search("서울", 5);

        assertFalse(sent.isEmpty(), "요청이 한 건도 안 나갔다 — 이 테스트가 아무것도 안 보고 있다");
        for (URI uri : sent) {
            assertFalse(uri.getRawQuery().contains("%25"),
                    "이중 인코딩됐다 — 카카오가 검색어 대신 퍼센트 문자열을 받는다: " + uri.getRawQuery());
            assertEquals("서울", queryOf(uri),
                    "카카오에 닿는 검색어가 사용자가 친 말과 다르다: " + uri.getRawQuery());
        }
    }

    /** 띄어쓰기가 든 주소도 같다 — 도로명주소가 이 모양이다. */
    @Test
    void 공백이_든_주소도_그대로_닿는다() {
        client("test-key").search("서초구 남부순환로 2567", 5);

        for (URI uri : sent) {
            assertEquals("서초구 남부순환로 2567", queryOf(uri), "공백이 든 주소가 망가졌다");
        }
    }

    /**
     * <b>size 를 카카오 상한으로 자른다.</b>
     *
     * <p>넘기는 값은 "허브로 채우고 남은 자리" 라 최대 20 인데, 키워드 검색은 15 를 넘기면 400 이다.
     * 허브가 5건 미만 걸리는 검색어 — 주소가 가장 필요한 그것들 — 이 전부 여기 걸렸다.
     */
    @Test
    void size_가_카카오_상한을_안_넘는다() {
        client("test-key").search("역삼", 20);

        assertFalse(sent.isEmpty(), "요청이 안 나갔다");
        for (URI uri : sent) {
            assertTrue(sizeOf(uri) <= 15, "size 가 카카오 상한을 넘는다 — 400 이 난다: " + uri.getRawQuery());
        }
    }

    /** 뒤집힌 쪽 — 상한 아래면 그대로 쓴다. 무조건 15 로 덮어쓰고 있지 않은지. */
    @Test
    void 상한_아래의_size_는_그대로_쓴다() {
        client("test-key").search("역삼", 6);

        assertEquals(6, sizeOf(sent.get(0)), "상한 아래인데 값을 바꿨다");
    }

    /** 키가 없으면 외부를 아예 안 부른다(로컬 실행성). */
    @Test
    void 키가_없으면_부르지_않는다() {
        List<FoundPlace> found = client("").search("서울", 5);

        assertTrue(sent.isEmpty(), "키가 없는데 외부를 불렀다");
        assertTrue(found.isEmpty());
    }

    private static String queryOf(URI uri) {
        return paramOf(uri, "query");
    }

    private static int sizeOf(URI uri) {
        return Integer.parseInt(paramOf(uri, "size"));
    }

    /** {@code getRawQuery} 를 직접 푼다 — {@code getQuery} 는 이미 디코딩돼 이중 인코딩을 못 본다. */
    private static String paramOf(URI uri, String name) {
        for (String pair : uri.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException(name + " 파라미터가 없다: " + uri);
    }
}
