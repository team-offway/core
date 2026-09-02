package com.offway.core.common.external;

import java.util.Objects;
import java.util.Set;

/**
 * 외부 API 호출을 일으킨 주체 — 배치 이름이거나 요청 엔드포인트다(#285).
 *
 * <p><b>왜 필요했나.</b> #257 의 한도 알림은 초과 사실만 말한다. 배치가 태웠는지 코스 생성이 태웠는지
 * 구분이 안 되니 그 알림을 받고도 할 수 있는 일이 없었다. 실제로 "매일 한도초과가 나는데 어디서 새는지
 * 모르겠다" 가 며칠째 안 풀렸고, 그동안 추정만 오갔다.
 *
 * <p><b>이름은 경로가 아니라 패턴이다.</b> {@code /api/v1/courses/123} 처럼 경로를 그대로 쓰면 id 마다
 * 다른 주체가 되어 키 공간에 상한이 없어진다. 패턴({@code POST /api/v1/courses/{id}})으로 두면 배치 5 개와
 * 엔드포인트 20 개 남짓으로 유한하다.
 *
 * @param name 사람이 읽는 주체 이름. 빈 값은 {@link #UNKNOWN} 으로 접힌다
 */
public record Caller(String name) {

    /** {@code external_api_call_caller.caller} 컬럼 폭. */
    private static final int MAX_LENGTH = 80;

    private static final String UNKNOWN_NAME = "미상";

    /**
     * 주체를 알 수 없는 호출.
     *
     * <p><b>버킷으로 남기는 것이 요점이다.</b> 맥락을 심는 것을 빠뜨린 경로가 생기면 미상 비중이 커져 눈에
     * 보인다. 아무 주체에나 붙이면 조용히 틀린 값이 되어 오히려 나쁘다.
     */
    public static final Caller UNKNOWN = new Caller(UNKNOWN_NAME);

    public Caller {
        Objects.requireNonNull(name, "caller name");
        name = normalize(name);
    }

    /** 배치처럼 이름이 정해진 주체. */
    public static Caller of(String name) {
        return new Caller(name);
    }

    /** HTTP 요청. 같은 경로라도 메서드가 다르면 다른 화면이라 함께 싣는다. */
    public static Caller request(String method, String pattern) {
        return new Caller(method + " " + pattern);
    }

    /**
     * 사용자 요청이 일으킨 호출인가 — 아니면 배치다(#398).
     *
     * <p><b>이 구분이 심사 자료의 핵심이다.</b> 총량만 보면 "우리가 API 를 쓴다" 까지밖에 못 말하는데,
     * 정작 보여야 하는 것은 <b>서비스가 요청마다 실제로 부른다</b>는 쪽이다. 9/1 관측에서 700 중
     * 603 이 배치였다.
     *
     * <p>판정은 <b>이름의 생김새</b>로 한다 — {@link #request} 가 만든 이름만 HTTP 메서드로 시작한다.
     * 별도 컬럼을 두지 않은 것은 이미 쌓인 기록에는 그 값이 없어서다. 이름에서 도출하면 과거 기록도
     * 같이 읽힌다.
     *
     * <p>다만 이건 <b>규약이지 강제가 아니다.</b> 배치 이름을 {@code "GET 무언가"} 로 지으면 사용자
     * 요청으로 잘못 세어진다. 지금 배치 이름은 전부 한국어라 닿지 않는 경로다.
     */
    public boolean fromRequest() {
        return REQUEST_METHODS.stream().anyMatch(method -> name.startsWith(method + " "));
    }

    /** {@link #request} 가 앞에 붙이는 값. Spring MVC 가 쓰는 표준 메서드다. */
    private static final Set<String> REQUEST_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private static String normalize(String raw) {
        String stripped = raw.strip();
        if (stripped.isEmpty()) {
            return UNKNOWN_NAME;
        }
        // 컬럼 폭을 넘기면 INSERT 가 통째로 실패해 그 호출의 귀속이 사라진다. 자르는 편이 낫다 —
        // 잘려서 두 패턴이 한 행으로 합쳐져도 "어느 쪽이 태웠나" 는 여전히 읽힌다.
        return stripped.length() <= MAX_LENGTH ? stripped : stripped.substring(0, MAX_LENGTH);
    }
}
