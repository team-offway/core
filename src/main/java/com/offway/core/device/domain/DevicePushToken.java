package com.offway.core.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기 하나의 FCM 푸시 토큰(#264) — 알림을 보낼 주소.
 *
 * <p><b>토큰이 곧 기기의 신원이다.</b> 소유 키가 아니라 토큰에 유니크 제약을 건다. 앱을 지웠다 깔면
 * 게스트 ID 는 새로 발급되지만 FCM 토큰은 이어질 수 있고, 그때 같은 기기에 두 행이 생기면 같은 알림이
 * 두 번 간다. 토큰을 기준으로 잡으면 나중에 온 등록이 앞의 것을 덮어써 항상 한 행으로 수렴한다.
 *
 * <p><b>토큰은 비밀값에 준한다.</b> 이 값을 아는 쪽은 그 기기로 알림을 보낼 수 있다. 로그·예외 메시지에
 * 그대로 싣지 않는다(로깅 규약).
 *
 * <p>한 소유자가 여러 행을 가질 수 있다 — 폰과 태블릿에서 같은 게스트로 쓰는 경우다.
 */
@Entity
@Table(name = "device_push_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevicePushToken {

    /** 소유 키 길이 — 코스({@code Course.MAX_GUEST_ID_LENGTH})·연차와 같은 값을 쓴다. */
    public static final int MAX_OWNER_ID_LENGTH = 64;

    /**
     * 토큰 칸 길이.
     *
     * <p>FCM 등록 토큰은 실제로 160자 안팎이지만 규격이 길이를 못 박지 않아 여유를 크게 둔다. 유니크
     * 인덱스가 걸리는 칸이라 무한정 늘릴 수는 없다 — utf8mb4 기준 2048바이트로, InnoDB 인덱스 키 상한
     * (3072바이트) 안이다.
     */
    public static final int MAX_TOKEN_LENGTH = 512;

    /** enum 이름을 담는 칸. 플랫폼이 늘어도 마이그레이션이 필요 없게 잡았다. */
    public static final int PLATFORM_LENGTH = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false, length = MAX_OWNER_ID_LENGTH)
    private String guestId;

    @Column(name = "token", nullable = false, unique = true, length = MAX_TOKEN_LENGTH)
    private String token;

    /** ordinal 이 아니라 이름으로 저장한다 — 상수를 재배치하면 이미 저장된 행의 뜻이 통째로 바뀐다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = PLATFORM_LENGTH)
    private DevicePlatform platform;

    /** 처음 등록한 시각. 재등록으로 갱신되지 않는다. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 마지막 등록 시각. 오래 조용한 토큰을 걷어낼 때 근거가 된다. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private DevicePushToken(
            String guestId, String token, DevicePlatform platform, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.guestId = requireOwner(guestId);
        this.token = requireToken(token);
        this.platform = Objects.requireNonNull(platform, "플랫폼은 필수입니다");
        this.createdAt = Objects.requireNonNull(createdAt, "등록 시각은 필수입니다");
        this.updatedAt = Objects.requireNonNull(updatedAt, "갱신 시각은 필수입니다");
    }

    /**
     * 등록 요청 하나를 값으로 만든다 — 시각이 입력에서 도출되므로 빌더가 아니라 팩토리다(조립이면 빌더,
     * 계산이면 팩토리).
     *
     * <p>처음 등록이든 재등록이든 같은 값을 만든다. <b>둘을 여기서 가르지 않는다</b> — 이미 있는지 보고
     * 갈라 쓰면 동시 요청에서 둘 다 "없다" 를 읽는다. 가르는 일은 유니크 제약을 쥔 DB 가 한다.
     */
    public static DevicePushToken register(String guestId, String token, DevicePlatform platform, LocalDateTime now) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다");
        return new DevicePushToken(guestId, token, platform, now, now);
    }

    /**
     * 소유 키 계약 검증(400).
     *
     * <p>빈 헤더({@code X-Guest-Id: " "})는 {@code @RequestHeader} 를 통과하므로 <b>멀쩡한 클라이언트가
     * 정상 요청으로 닿는다</b> — 불변식으로 다루면 500 이 나간다.
     */
    public static String requireOwner(String guestId) {
        if (guestId == null || guestId.isBlank() || guestId.length() > MAX_OWNER_ID_LENGTH) {
            throw DeviceException.invalidOwnerId();
        }
        return guestId;
    }

    /**
     * 토큰 계약 검증(400).
     *
     * <p><b>예외 메시지에 토큰을 싣지 않는다.</b> detail 은 응답에 그대로 나가고 로그에도 남는다 — 길이가
     * 문제였다는 사실만 알리고 값은 남기지 않는다.
     */
    public static String requireToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw DeviceException.invalidPushToken();
        }
        return token;
    }
}
