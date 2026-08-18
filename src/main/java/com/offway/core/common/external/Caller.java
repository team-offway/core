package com.offway.core.common.external;

import java.util.Objects;

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
