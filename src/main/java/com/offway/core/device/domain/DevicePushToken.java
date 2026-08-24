package com.offway.core.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기 하나의 FCM 푸시 토큰(#264) — 알림을 보낼 주소.
 *
 * <p><b>유니크 제약은 (소유자, 토큰) 복합이다.</b> 토큰 단독으로 걸면 같은 토큰이 다른 소유자로 왔을 때
 * 소유자를 갈아끼우게 되고, 그러면 <b>남의 토큰을 아는 쪽이 그것을 자기 것으로 등록해 상대의 푸시를
 * 끊고 자기 알림을 상대 기기로 보낼 수 있다.</b> 복합 키로 두면 남의 행은 건드릴 수 없고 자기 소유의
 * 행이 하나 늘 뿐이다.
 *
 * <p><b>소유자 사칭 자체는 인증이 막는다</b>(#280). 등록이 헤더가 아니라 access 토큰이 확인한 사용자를
 * 소유 키로 쓰므로, 남의 소유자를 적어 보낼 길이 없다. 복합 키는 그 위에 한 겹 더 두는 것이다.
 *
 * <p>기기를 바꾸거나 앱을 다시 깔면 <b>같은 사람에게 토큰이 여러 개</b> 남는다(옛 기기의 행은 주인이
 * 다시 오지 않는 죽은 행이다). 그건 발송 단계에서 토큰 기준 중복 제거와 FCM 의 {@code UNREGISTERED}
 * 응답으로 정리한다(#270).
 *
 * <p><b>토큰은 비밀값에 준한다.</b> 이 값을 아는 쪽은 그 기기로 알림을 보낼 수 있다. 로그·예외 메시지에
 * 그대로 싣지 않는다(로깅 규약).
 *
 * <p>한 소유자가 여러 행을 가질 수 있다 — 폰과 태블릿에서 같은 게스트로 쓰는 경우다.
 */
@Entity
@Table(
        name = "device_push_token",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_device_push_token_owner_token",
                        columnNames = {"guest_id", "token"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevicePushToken {

    /**
     * 소유 키 길이.
     *
     * <p>예전에는 코스·연차의 같은 상수를 따라 적은 값이었는데, 그쪽 소유가 {@code user_id}(UUID)로
     * 옮겨가며(#280) 따라갈 상수가 사라졌다. <b>기기는 그 전환의 대상이 아니라</b>(여기서 대상은 사람이
     * 아니라 기기다) 이 칸은 남았고, 이제 이 값은 device 가 단독으로 소유한다 — 아래 {@link #MAX_TOKEN_LENGTH}
     * 주석의 인덱스 키 상한 계산이 이 값에 걸려 있다.
     */
    public static final int MAX_OWNER_ID_LENGTH = 64;

    /**
     * 토큰 칸 길이.
     *
     * <p>FCM 등록 토큰은 실제로 160자 안팎이지만 규격이 길이를 못 박지 않아 여유를 크게 둔다. 유니크
     * 인덱스가 걸리는 칸이라 무한정 늘릴 수는 없다 — utf8mb4 기준 2048바이트이고, 소유 키(64자=256바이트)와
     * 묶인 복합 유니크라 합쳐 2304바이트로 InnoDB 인덱스 키 상한(3072바이트) 안이다.
     */
    public static final int MAX_TOKEN_LENGTH = 512;

    /** enum 이름을 담는 칸. 플랫폼이 늘어도 마이그레이션이 필요 없게 잡았다. */
    public static final int PLATFORM_LENGTH = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false, length = MAX_OWNER_ID_LENGTH)
    private String guestId;

    @Column(name = "token", nullable = false, length = MAX_TOKEN_LENGTH)
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
     * 소유 키 불변식 검증.
     *
     * <p>소유 키가 요청 헤더이던 시절에는 빈 값이 {@code @RequestHeader} 를 통과해 <b>멀쩡한 클라이언트가
     * 정상 요청으로 닿는</b> 자리였다. 지금은 인증이 확인한 UUID 를 문자열로 넘기므로 여기 닿는 빈 값은
     * <b>우리 코드의 버그</b>다 — 그래도 검증은 남긴다. 도메인은 누가 만들든 스스로 유효함을 보장하는
     * 최후의 보루이고, 이 검증이 없으면 잘못된 값이 조용히 저장돼 발송이 0건이 된다.
     */
    public static String requireOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > MAX_OWNER_ID_LENGTH) {
            throw DeviceException.invalidOwnerId();
        }
        return owner;
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
