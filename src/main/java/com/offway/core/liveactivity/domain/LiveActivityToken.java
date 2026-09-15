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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 잠금화면에 띄운 여행 하나의 갱신 주소(#575).
 *
 * <h2>{@code device_push_token} 과 왜 따로 두나</h2>
 *
 * <p><b>가리키는 것이 다르다.</b> FCM 토큰은 <b>기기</b> 하나를 가리키고 앱을 지울 때까지 산다. Live
 * Activity 토큰은 <b>잠금화면에 띄운 카드</b> 하나를 가리키고, 그 카드가 사라지면 함께 죽는다 — 같은
 * 기기에서 코스 둘을 띄우면 토큰도 둘이다.
 *
 * <p>한 표에 섞으면 두 가지가 깨진다. 기기 단위 발송(FCM)이 Live Activity 토큰으로 나가고, 반대로
 * 잠금화면 갱신이 FCM 토큰으로 나간다. 둘 다 조용히 실패하는 종류라 아무 흔적을 안 남긴다.
 *
 * <h2>유니크는 (사용자, 코스)</h2>
 *
 * <p>한 사람이 같은 코스를 잠금화면에 두 번 띄울 이유가 없다. 앱이 재시도할 수 있게 등록은 <b>멱등</b>
 * 이어야 하는데(네트워크가 끊겼을 때 성공했는지 앱이 알 수 없다), 그 판정을 애플리케이션에서 조회 후
 * 분기로 하면 동시 요청이 둘 다 "없다" 를 읽는다. 제약을 쥔 DB 가 한 문장 안에서 가르게 한다.
 *
 * <p><b>토큰에는 유니크를 걸지 않는다.</b> 토큰이 신원인 FCM 과 달리 여기서 신원은 (사람, 코스)다.
 * 토큰은 같은 카드가 다시 뜨면 바뀌는 값이라, 그쪽에 제약을 걸면 갱신이 제약 위반으로 떨어진다.
 *
 * <h2>이 토큰은 짧게 산다</h2>
 *
 * <p>iOS 는 Live Activity 를 <b>최대 8시간 뒤 스스로 종료</b>하고 그때 토큰이 죽는다. 하루 한 번 도는
 * 갱신은 그래서 <b>죽은 토큰을 자주 만나고, 그것이 정상</b>이다 — 앱이 다시 띄우면서 새 토큰을 올린다.
 * 발송이 {@code 410 Gone} 을 받으면 그 행을 지운다.
 *
 * <p><b>토큰은 비밀값에 준한다.</b> 이 값을 아는 쪽은 그 사람의 잠금화면에 내용을 그릴 수 있다.
 * 로그·예외 메시지에 그대로 싣지 않는다(로깅 규약).
 */
@Entity
@Table(
        name = "live_activity_token",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_live_activity_token_user_course",
                        columnNames = {"user_id", "course_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveActivityToken {

    /**
     * 토큰 칸 길이.
     *
     * <p>Live Activity push token 은 hex 문자열이고 실제로는 160자 안팎이다. 규격이 길이를 못 박지
     * 않아 여유를 두되, 무한정 늘리지는 않는다 — 앱이 잘못된 값을 올렸을 때 그것이 그대로 저장되는
     * 것보다 400 으로 거절하는 편이 원인을 빨리 드러낸다.
     */
    public static final int MAX_TOKEN_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주인. 등록 요청의 본문이 아니라 <b>access 토큰이 확인한 사용자</b>다(#280). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    /**
     * 잠금화면에 띄운 코스.
     *
     * <p>도메인 경계를 넘는 참조라 raw id 다(영속성 규약). 코스가 지워져도 이 행은 남을 수 있는데,
     * 그때는 갱신 경로가 코스를 못 찾아 종료를 보내고 행을 지운다.
     */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "token", nullable = false, length = MAX_TOKEN_LENGTH)
    private String token;

    /** 처음 등록한 시각. 재등록으로 갱신되지 않는다. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 마지막 등록 시각. 토큰이 언제 갈렸는지를 보는 자리다. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private LiveActivityToken(
            UUID userId, Long courseId, String token, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId = Objects.requireNonNull(userId, "주인은 필수입니다");
        this.courseId = requireCourseId(courseId);
        this.token = requireToken(token);
        this.createdAt = Objects.requireNonNull(createdAt, "등록 시각은 필수입니다");
        this.updatedAt = Objects.requireNonNull(updatedAt, "갱신 시각은 필수입니다");
    }

    /**
     * 등록 요청 하나를 값으로 만든다 — 시각이 입력에서 도출되므로 빌더가 아니라 팩토리다(조립이면
     * 빌더, 계산이면 팩토리).
     *
     * <p><b>처음 등록과 재등록을 여기서 가르지 않는다.</b> 이미 있는지 보고 갈라 쓰면 동시 요청에서
     * 둘 다 "없다" 를 읽는다. 가르는 일은 유니크 제약을 쥔 DB 가 한다.
     */
    public static LiveActivityToken register(UUID userId, Long courseId, String token, LocalDateTime now) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다");
        return new LiveActivityToken(userId, courseId, token, now, now);
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
        return token;
    }

    /** 코스 식별자 계약 검증(400) — 앱이 보낸 값이라 멀쩡한 클라이언트가 정상 요청으로 닿는다. */
    public static Long requireCourseId(Long courseId) {
        if (courseId == null || courseId <= 0) {
            throw LiveActivityException.invalidCourseId();
        }
        return courseId;
    }
}
