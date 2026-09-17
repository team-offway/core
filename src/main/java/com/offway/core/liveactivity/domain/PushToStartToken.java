package com.offway.core.liveactivity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 기기 하나에 <b>잠금화면 카드를 띄울 수 있는</b> 주소(#583).
 *
 * <h2>{@link LiveActivityToken} 과 무엇이 다른가</h2>
 *
 * <p>가리키는 대상이 다르다.
 *
 * <pre>
 *   LiveActivityToken   (사용자, 코스)  이미 떠 있는 <b>카드 하나</b>  카드가 사라지면 죽는다
 *   PushToStartToken    (사용자, 기기)  그 <b>기기</b>                앱을 지울 때까지 산다
 * </pre>
 *
 * <p>그래서 이 토큰은 <b>카드를 한 번도 띄운 적이 없어도</b> 발급된다. 서버가 처음으로 카드를 만드는
 * 데 쓰는 것이 이것이다.
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>iOS 는 Live Activity 를 <b>최대 8시간</b> 뒤 스스로 끝낸다. 그리고 카드를 <b>처음 띄우는</b> 일은
 * 지금까지 앱만 할 수 있었다. 둘이 겹쳐 두 가지 빈틈이 생겼다.
 *
 * <ul>
 *   <li>출발 5일 전부터 띄우기로 했는데, 그 5일 동안 <b>앱을 안 연 사람은 끝내 못 본다</b>
 *   <li>자정 갱신(#577)이 실제로 먹는 것은 <b>그날 16시 이후에 앱을 연 경우뿐</b>이다 — 낮 12시에
 *       띄운 카드는 20시에 끝나 있어 자정 푸시가 갈 카드가 없다
 * </ul>
 *
 * <p>push-to-start 는 그 "첫 띄우기" 를 서버로 옮긴다. 띄운 뒤의 갱신·종료는 #577 경로를 그대로 탄다.
 *
 * <h2>유니크는 (사용자, 토큰)</h2>
 *
 * <p>한 사람이 폰과 태블릿을 쓰면 행이 둘이다 — 둘 다에 카드를 띄워야 한다. 앱은 시작할 때마다 같은
 * 토큰을 다시 보내므로 등록이 <b>멱등</b>이어야 하고, 그 판정은 제약을 쥔 DB 가 한 문장 안에서 한다.
 *
 * <p><b>토큰 단독 유니크가 아니다.</b> 그렇게 걸면 같은 토큰이 다른 소유자로 왔을 때 주인을 갈아끼우게
 * 되고, 남의 토큰을 아는 쪽이 그것을 자기 것으로 등록해 상대의 카드를 가로챌 수 있다
 * ({@code device_push_token} 이 같은 이유로 복합 키다).
 *
 * <p><b>토큰은 비밀값에 준한다.</b> 이 값을 아는 쪽은 그 기기 잠금화면에 카드를 만들 수 있다.
 * 로그·예외 메시지·URL 에 그대로 싣지 않는다(로깅 규약).
 */
@Entity
@Table(
        name = "push_to_start_token",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_push_to_start_token_user_token",
                        columnNames = {"user_id", "token"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToStartToken {

    /**
     * 토큰 칸 길이.
     *
     * <p>APNs push-to-start 토큰은 hex 문자열이고 실제로는 160자 안팎이다. 규격이 길이를 못 박지
     * 않아 여유를 두되, 무한정 늘리지 않는다 — 유니크 인덱스가 걸리는 칸이라 소유 키(16바이트)와
     * 합쳐 InnoDB 인덱스 키 상한(3072바이트) 안에 있어야 한다. utf8mb4 기준 512자면 2048바이트다.
     */
    public static final int MAX_TOKEN_LENGTH = 512;

    /**
     * 토큰의 생김새 — <b>hex 문자열, 짝수 길이</b>.
     *
     * <p>{@code Activity.pushToStartTokenUpdates} 가 주는 것은 {@code Data} 이고, 앱은 그것을
     * 바이트마다 {@code %02x} 로 풀어 보낸다. 그래서 hex 문자만 들어 있고 길이가 항상 짝수다 —
     * {@code (?:[0-9A-Fa-f]{2})+} 하나가 그 둘을 함께 강제한다.
     *
     * <p><b>왜 형식까지 보나.</b> 이 값은 그대로 APNs 요청 URL 에 붙는다. 공백이 섞인 값이 들어오면
     * {@code URI.create} 가 터지고, 그 실패는 {@link com.offway.core.liveactivity.infrastructure.apns.ApnsResult#FAILED}
     * 로 번역된다 — <b>{@code GONE} 이 아니라서 행이 안 지워지고 매일 같은 실패를 되풀이한다.</b>
     * 여기서 한 번 막는 것이 그 반복을 없애는 유일한 자리다.
     */
    public static final String TOKEN_PATTERN = "^(?:[0-9A-Fa-f]{2})+$";

    private static final Pattern HEX = Pattern.compile(TOKEN_PATTERN);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주인. 등록 요청의 본문이 아니라 <b>access 토큰이 확인한 사용자</b>다(#280). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "token", nullable = false, length = MAX_TOKEN_LENGTH)
    private String token;

    /** 처음 등록한 시각. 재등록으로 갱신되지 않는다. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 마지막 등록 시각. 앱이 언제까지 살아 있었는지를 보는 자리다. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PushToStartToken(UUID userId, String token, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId = Objects.requireNonNull(userId, "주인은 필수입니다");
        this.token = requireToken(token);
        this.createdAt = Objects.requireNonNull(createdAt, "등록 시각은 필수입니다");
        this.updatedAt = Objects.requireNonNull(updatedAt, "갱신 시각은 필수입니다");
    }

    /**
     * 등록 요청 하나를 값으로 만든다 — 시각이 입력에서 도출되므로 빌더가 아니라 팩토리다(조립이면
     * 빌더, 계산이면 팩토리).
     */
    public static PushToStartToken register(UUID userId, String token, LocalDateTime now) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다");
        return new PushToStartToken(userId, token, now, now);
    }

    /**
     * 토큰 계약 검증(400).
     *
     * <p><b>예외 메시지에 토큰을 싣지 않는다.</b> detail 은 응답에 그대로 나가고 로그에도 남는다 —
     * 길이가 문제였다는 사실만 알리고 값은 남기지 않는다.
     */
    public static String requireToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw LiveActivityException.invalidPushToken();
        }
        if (!HEX.matcher(token).matches()) {
            throw LiveActivityException.invalidPushToken();
        }
        return token;
    }
}
