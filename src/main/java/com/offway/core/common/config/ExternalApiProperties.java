package com.offway.core.common.config;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 API 키. 값이 없어도(빈 문자열) 부팅된다(로컬 실행성 규칙) — 실제 호출만 비활성.
 *
 * <p><b>위치 인수 생성자를 직접 부르지 않는다.</b> 컴포넌트를 더할 때마다 모든 호출부가 깨진다 —
 * 카카오 키를 더하면서 테스트 31곳이 그렇게 깨졌고, 같은 함정을 {@code CourseResponse.Item} 에서
 * 세 번 겪었다(#317). 키 하나만 쓰면 {@code ofDataGoKr}·{@code ofTmap}·{@code ofKakao}, 여럿이면
 * 빌더를 쓴다.
 *
 * <p>data.go.kr 인증키 하나로 특일정보·TourAPI·관광빅데이터·TAGO·코레일 공용.
 */
@ConfigurationProperties(prefix = "offway.external")
@Builder
public record ExternalApiProperties(DataGoKr dataGoKr, Tmap tmap, Kakao kakao) {

    public ExternalApiProperties {
        if (dataGoKr == null) {
            dataGoKr = new DataGoKr(null);
        }
        if (tmap == null) {
            tmap = new Tmap(null);
        }
        if (kakao == null) {
            kakao = new Kakao(null);
        }
    }

    /**
     * data.go.kr 키만 쓰는 호출부용 — 나머지는 키 없음으로 둔다.
     *
     * <p><b>왜 팩토리를 두나.</b> 이 record 의 위치 인수 생성자를 직접 부르면 <b>컴포넌트를 더할 때마다
     * 모든 호출부가 깨진다</b> — 카카오 키를 더하면서 테스트 31곳이 그렇게 깨졌다. 같은 함정을
     * {@code CourseResponse.Item} 에서 세 번 겪었다(#317).
     *
     * <p>호출부는 자기가 쓰는 키 하나만 말하면 되고, 나머지는 이 팩토리가 채운다. 그러면 다음 키가
     * 늘어도 이 파일만 고친다.
     */
    public static ExternalApiProperties ofDataGoKr(String serviceKey) {
        return new ExternalApiProperties(new DataGoKr(serviceKey), null, null);
    }

    /** TMAP 키만 쓰는 호출부용. */
    public static ExternalApiProperties ofTmap(String appKey) {
        return new ExternalApiProperties(null, new Tmap(appKey), null);
    }

    /** 카카오 REST 키만 쓰는 호출부용(#590). */
    public static ExternalApiProperties ofKakao(String restApiKey) {
        return new ExternalApiProperties(null, null, new Kakao(restApiKey));
    }

    public record DataGoKr(String serviceKey) {
        public boolean hasKey() {
            return serviceKey != null && !serviceKey.isBlank();
        }
    }

    public record Tmap(String appKey) {
        public boolean hasKey() {
            return appKey != null && !appKey.isBlank();
        }
    }

    /**
     * 카카오 REST 키 — 출발지 주소·장소 검색(카카오 로컬)이 쓴다(#590).
     *
     * <p><b>OAuth 쪽과 같은 환경변수({@code KAKAO_REST_API_KEY})를 읽는다.</b> 카카오는 REST 키 하나로
     * 로그인과 로컬 검색을 함께 쓰게 하므로 값이 같다. 그래도 설정 자리를 나눈 이유는 <b>읽는 쪽이
     * 다르기 때문</b>이다 — 로그인은 {@code offway.auth}, 출발지 검색은 {@code offway.external} 을 본다.
     * transport 가 user 의 설정을 읽으면 도메인 경계가 새고, 나중에 키를 갈라야 할 때 그 자리를 못 찾는다.
     */
    public record Kakao(String restApiKey) {
        public boolean hasKey() {
            return restApiKey != null && !restApiKey.isBlank();
        }
    }
}
