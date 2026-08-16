package com.offway.core.common.logging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 로그에 남기기 전에 쿼리스트링의 민감한 값을 가린다.
 *
 * <p><b>화이트리스트가 아니라 거부 목록으로 간다.</b> 화이트리스트면 파라미터가 늘 때마다 등록을 빠뜨려
 * 조용히 안 찍힌다 — 로그에서 그 사실은 눈에 띄지 않는다.
 *
 * <p><b>소셜 로그인이 붙으면서 규칙을 다시 봤다(#34).</b> 예전 주석이 "OAuth 로 실사용자가 들어오면 다시 본다"
 * 고 남긴 그 시점이다. 이제 요청·예외 메시지에 provider 액세스 토큰·ID 토큰·우리 refresh 토큰이 흐른다.
 * {@code token} 만으로는 <b>안 걸린다</b> — {@code accessToken=...} 은 {@code token} 앞이 단어 문자라 경계가
 * 성립하지 않아 그대로 로그에 박힌다. 그래서 이름 목록과 정규식 양쪽에 {@code *token} 변형을 넣었다.
 */
public final class SensitiveParams {

    /**
     * 이름이 이 중 하나면 값을 가린다. 비교는 소문자로 한다.
     *
     * <p>토큰은 {@code token} 하나로 뭉뚱그릴 수 없다 — 실제로 오는 이름이 {@code accessToken}·{@code idToken}·
     * {@code refreshToken}·{@code identityToken} 이라 각각 적어야 걸린다.
     *
     * <p><b>snake_case 도 함께 적는다.</b> 우리 앱은 camelCase 로 보내지만 OAuth 규격(RFC 6749)이 쓰는 이름은
     * {@code access_token}·{@code id_token}·{@code refresh_token} 이다. 제공자 쪽 URL 이 예외 메시지에 실려
     * 들어오는 경로가 있어, 한쪽만 적으면 그 경로로 토큰이 그대로 로그에 남는다.
     */
    private static final Set<String> MASKED_NAMES = Set.of(
            "servicekey",
            "appkey",
            "password",
            "token",
            "accesstoken",
            "access_token",
            "id_token",
            "refresh_token",
            "identity_token",
            "idtoken",
            "identitytoken",
            "refreshtoken",
            "secret",
            "client_secret",
            "authorization");

    private static final String MASK = "***";

    /** 사람이 읽는 목록 구분자 — 쿼리스트링의 {@code &} 보다 눈에 덜 걸린다. */
    private static final String READABLE_DELIMITER = ", ";

    /**
     * 값 하나의 표시 상한. 넘으면 잘라내고 표식을 붙인다.
     *
     * <p>로그 한 줄은 훑어보는 것이라 긴 값 하나가 줄 전체를 밀어내면 나머지를 못 읽는다. 전문이 필요하면
     * 요청 본문·추적 id 로 찾는다.
     */
    private static final int MAX_VALUE_LENGTH = 60;

    private static final String TRUNCATED = "…";

    /**
     * 로그에 그대로 실으면 안 되는 문자 — 제어문자 전부.
     *
     * <p><b>디코딩하는 순간 생기는 위험이다.</b> {@code %0A} 가 실제 개행이 되면 값 하나가 로그를 여러 줄로
     * 쪼개, 공격자가 가짜 로그 줄을 지어낼 수 있다. 인코딩된 채로 찍을 때는 없던 문제라 디코딩과 함께 막는다.
     */
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    /**
     * 자유 텍스트에서 {@code 민감이름=값} 을 찾는다 — 값은 {@code &}·공백·따옴표·닫는 괄호 앞까지.
     *
     * <p>쿼리스트링 파서를 쓰지 않는 이유는 입력이 쿼리가 아니라 <b>아무 문장</b>(예외 메시지)이기 때문이다.
     *
     * <p>{@code token} 앞에 {@code [\w-]*} 를 붙여 {@code accessToken}·{@code id_token} 같은 변형까지 잡는다.
     * 이게 없으면 {@code \btoken=} 이 {@code accessToken=} 을 놓친다 — 앞 글자가 단어 문자라 경계가 없다.
     */
    private static final Pattern SECRET_ASSIGNMENT =
            Pattern.compile("(?i)\\b(serviceKey|appKey|password|[\\w-]*token|[\\w-]*secret)=[^&\\s\"')\\]]*");

    /**
     * 디스코드 웹훅 URL 의 token 조각 — {@code .../webhooks/{id}/{token}}(#257).
     *
     * <p><b>{@link #SECRET_ASSIGNMENT} 로는 안 걸린다.</b> 그쪽은 {@code 이름=값} 을 찾는데 웹훅 토큰은
     * 경로 조각이라 이름이 없다. 이 URL 끝을 아는 사람은 누구나 우리 채널에 글을 쓸 수 있어, 전송 실패
     * 메시지에 URL 이 섞여 들어오는 경로를 함께 막는다.
     */
    private static final Pattern DISCORD_WEBHOOK =
            Pattern.compile("(?i)(/api/webhooks/\\d+/)[\\w-]+");

    /**
     * {@code Bearer <토큰>} — 헤더 형태라 {@link #SECRET_ASSIGNMENT}({@code 이름=값})로는 안 걸린다(#34).
     *
     * <p>소셜 로그인부터 우리 요청에 {@code Authorization: Bearer} 가 실린다. 외부 호출 실패 예외 메시지에 요청
     * 헤더가 섞여 오면 액세스 토큰이 통째로 로그에 남는데, 그 토큰은 <b>그대로 카카오 프로필을 부를 수 있는 값</b>
     * 이다. JWT 는 점을 포함하므로 값 문자 집합에 {@code .} 을 넣는다.
     */
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\b(Bearer)\\s+[\\w.~\\-+/=]+");
    private static final String PAIR_DELIMITER = "&";
    private static final String NAME_VALUE_DELIMITER = "=";
    private static final int NAME_VALUE_LIMIT = 2;

    private SensitiveParams() {}



    /**
     * 사람이 읽을 수 있는 파라미터 목록 — 로그용.
     *
     * <p>{@code request.getQueryString()} 은 인코딩된 원문이라 한글이 {@code %ec%b6%a9...} 으로 나가 눈으로
     * 못 읽는다. 디코딩해서 {@code region=충청남도} 로 보여준다.
     *
     * <p>마스킹 규칙은 그대로 탄다 — 읽기 좋게 만드느라 가려야 할 값을 드러내면 안 된다.
     *
     * @return {@code "region=충청남도, kind=FOOD"}. 파라미터가 없으면 빈 문자열
     */
    public static String readableParams(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return Arrays.stream(query.split(PAIR_DELIMITER))
                .map(SensitiveParams::readablePair)
                .filter(pair -> !pair.isEmpty())
                .collect(Collectors.joining(READABLE_DELIMITER));
    }

    private static String readablePair(String pair) {
        String[] parts = pair.split(NAME_VALUE_DELIMITER, NAME_VALUE_LIMIT);
        String name = decodedForLog(parts[0]);
        if (parts.length < NAME_VALUE_LIMIT) {
            return name; // 등호 없는 조각 — 이름만 남긴다
        }
        // 마스킹 판정은 화면에 나갈 이름으로 한다. 원문으로 판정하면 우회가 생긴다 — to%0Aken 은
        // 원문 기준으로 개행이 낀 이름이라 매칭을 피하는데, 렌더링은 제어문자를 지워 "token=secret"
        // 으로 나간다. 판정과 표시가 갈리면 그 틈이 곧 구멍이다.
        if (MASKED_NAMES.contains(name.trim().toLowerCase(Locale.ROOT))) {
            return name + NAME_VALUE_DELIMITER + MASK;
        }
        return name + NAME_VALUE_DELIMITER + decodedForLog(parts[1]);
    }

    /** 디코딩 → 제어문자 제거 → 길이 제한. 이 순서를 지켜야 디코딩으로 생긴 개행이 걸러진다. */
    private static String decodedForLog(String raw) {
        String decoded;
        try {
            decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            decoded = raw; // 깨진 인코딩 — 로그를 찍다가 요청을 죽이지 않는다
        }
        return forLog(decoded);
    }

    /**
     * <b>이미 디코딩된 값 하나</b>를 로그에 실을 수 있게 — 제어문자 제거 → 길이 제한.
     *
     * <p><b>왜 필요한가.</b> {@code @PathVariable}·외부 응답 필드처럼 우리가 만들지 않은 문자열을 로그에
     * 그대로 끼우면 두 가지가 깨진다. ① {@code %0A} 는 서블릿이 이미 디코딩해 <b>실제 개행</b>으로 오므로
     * 값 하나가 로그를 여러 줄로 쪼개 가짜 로그 줄을 지어낼 수 있다. ② 길이 상한이 없어 값 하나가 줄
     * 전체를 밀어낸다. {@link #readableParams}·{@code RootCause} 가 쿼리·예외 메시지에 대해 이미 하는
     * 처리를 <b>값 하나</b>에도 같은 기준으로 적용한다 — 규칙이 두 벌이 되면 한쪽이 반드시 뒤처진다.
     *
     * <p><b>여기서 다시 디코딩하지 않는다.</b> 이미 디코딩된 값을 또 풀면 값 안의 {@code %41} 이
     * {@code A} 로 바뀌어, 로그가 실제 요청과 다른 값을 가리킨다.
     *
     * <p>가리지는 않는다 — 마스킹 대상은 {@code serviceKey} 류 비밀값이지, 지역 id·콘텐츠 id 같은 공개
     * 식별자가 아니다. 그것까지 가리면 "어느 것이 실패했나" 에 답하지 못해 로그의 존재 이유가 사라진다.
     *
     * @param value 로그에 실을 값. null 이면 빈 문자열
     */
    public static String forLog(String value) {
        String safe = stripControlChars(value);
        return safe.length() <= MAX_VALUE_LENGTH ? safe : safe.substring(0, MAX_VALUE_LENGTH) + TRUNCATED;
    }

    /**
     * 자유 텍스트 안의 {@code 이름=값} 중 민감한 것을 가린다 — 예외 메시지용.
     *
     * <p><b>왜 필요한가.</b> {@code WebClientResponseException} 메시지에는 요청 URL 이 통째로 들어올 수 있고,
     * 우리 외부 호출은 {@code serviceKey} 를 쿼리에 싣는다. 메시지를 로그에 그대로 옮기면 키가 샌다 —
     * 로깅 규약이 "URL 쿼리스트링을 그대로 남기지 않는다" 고 못박은 지점이다.
     *
     * <p>쿼리스트링 파서를 쓰지 않는 이유는 입력이 쿼리가 아니라 <b>아무 문장</b>이기 때문이다. 값의 끝은
     * {@code &}·공백·따옴표·닫는 괄호 중 먼저 오는 것으로 본다.
     */
    public static String maskSecretsInText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String assignmentsMasked = SECRET_ASSIGNMENT.matcher(text).replaceAll(secretReplacement());
        String webhooksMasked = DISCORD_WEBHOOK.matcher(assignmentsMasked).replaceAll("$1" + MASK);
        return BEARER_TOKEN.matcher(webhooksMasked).replaceAll("$1 " + MASK);
    }

    private static String secretReplacement() {
        return "$1" + NAME_VALUE_DELIMITER + MASK;
    }

    /**
     * 제어문자를 걷어낸다 — 로그 한 줄이 여러 줄로 쪼개지지 않게.
     *
     * <p>디코딩하거나 외부 메시지를 옮길 때 생기는 위험이다. {@code %0A} 가 실제 개행이 되면 값 하나가
     * 로그를 여러 줄로 쪼개, 공격자가 가짜 로그 줄을 지어낼 수 있다.
     */
    public static String stripControlChars(String text) {
        return text == null ? "" : CONTROL_CHARS.matcher(text).replaceAll("");
    }
}
