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
            dataGoKr = DataGoKr.of(null);
        }
        if (tmap == null) {
            tmap = Tmap.of(null);
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
        return new ExternalApiProperties(DataGoKr.of(serviceKey), null, null);
    }

    /** TMAP 키만 쓰는 호출부용. */
    public static ExternalApiProperties ofTmap(String appKey) {
        return new ExternalApiProperties(null, Tmap.of(appKey), null);
    }

    /** 카카오 REST 키만 쓰는 호출부용(#590). */
    public static ExternalApiProperties ofKakao(String restApiKey) {
        return new ExternalApiProperties(null, null, new Kakao(restApiKey));
    }

    /**
     * data.go.kr 인증키 — 주 키와 <b>보조 키</b>(#596).
     *
     * <h2>왜 보조 키를 두나</h2>
     *
     * <p>주 키의 한도가 마르면 그 뒤로는 실패다. 심사처럼 <b>다시 할 수 없는 자리</b>에서는 그
     * 실패가 그대로 결과가 된다. 보조 키는 <b>별도 한도</b>를 가지므로 주 키가 마른 뒤에도 산다.
     *
     * <p><b>키를 늘려 한도를 두 배로 쓰자는 것이 아니다.</b> 평소에는 주 키만 쓰고, 주 키가
     * 실패했을 때만 한 번 더 간다. 정상일 때 호출 수는 그대로다.
     *
     * @param serviceKey 주 인증키
     * @param fallbackKey 보조 인증키. 없으면 {@code null} — 폴백을 안 쓴다는 뜻이지 오류가 아니다
     */
    public record DataGoKr(String serviceKey, String fallbackKey) {

        /**
         * 주 키만 쓰는 호출부용.
         *
         * <p><b>nested record 에도 팩토리를 둔다.</b> 컴포넌트를 더하면 위치 인수 생성자를 쓰는
         * 호출부가 전부 깨진다 — 바깥 record 에서 이미 세 번 겪은 함정이다(#317). 여기서 같은 일이
         * 또 일어나지 않게 지금 막아 둔다.
         */
        public static DataGoKr of(String serviceKey) {
            return new DataGoKr(serviceKey, null);
        }

        public boolean hasKey() {
            return serviceKey != null && !serviceKey.isBlank();
        }

        /** 보조 키가 있나 — 없으면 주 키가 실패해도 더 해볼 것이 없다. */
        public boolean hasFallback() {
            return fallbackKey != null && !fallbackKey.isBlank();
        }
    }

    /**
     * TMAP 키 — 주 키와 보조 키(#596).
     *
     * <p><b>여기가 가장 급했다.</b> 경유지 최적화는 일일 한도가 50 으로 우리가 가진 것 중 가장
     * 빡빡하고, 자차 코스 하나가 날짜 수만큼 부르므로 2박3일이면 17건에 마른다. 마르면 직선거리로
     * 떨어지는데 <b>응답이 200</b> 이라 순서가 틀린 줄 화면에서 알 수 없다.
     */
    public record Tmap(String appKey, String fallbackKey) {

        /** 주 키만 쓰는 호출부용 — {@link DataGoKr#of} 와 같은 이유로 둔다. */
        public static Tmap of(String appKey) {
            return new Tmap(appKey, null);
        }

        public boolean hasKey() {
            return appKey != null && !appKey.isBlank();
        }

        public boolean hasFallback() {
            return fallbackKey != null && !fallbackKey.isBlank();
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
