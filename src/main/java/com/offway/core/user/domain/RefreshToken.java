package com.offway.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * refresh 토큰. 원문이 아니라 SHA-256 해시만 저장한다 — DB 가 유출돼도 토큰이 그대로 쓰이지 않게.
 *
 * <p>회전할 때 행을 지우지 않고 {@code revokedAt} 을 채운다. 지워버리면 "폐기된 토큰 재사용"과 "처음부터 없는 토큰"이
 * 똑같이 "조회 결과 없음"이 되어 탈취를 감지할 방법이 사라진다.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    /**
     * 회전 직후 <b>유예 창</b> — 이 안에 같은 토큰이 다시 오면 탈취가 아니라 재시도로 본다.
     *
     * <p>정상 앱도 같은 refresh 를 두 번 쏜다. 401 을 받은 요청 둘이 동시에 재발급을 걸거나, 응답을 못 받고
     * 타임아웃 재시도를 하면 그렇다. 그때마다 탈취 경보가 울려 세션 전체를 끊으면 <b>이긴 요청이 방금 받아 간
     * 정상 토큰까지 죽어</b> 사용자가 멀쩡한 토큰을 들고 로그아웃된다.
     *
     * <p>10초인 근거: 이 서비스의 외부 호출 timeout 상한이 3초라, 한 번 실패하고 재시도하는 데 걸리는 시간이
     * 그 몇 배를 넘지 않는다. 대가는 이 창 안에서는 탈취된 토큰이 다시 와도 <b>경보가 울리지 않는다</b>는 것이라
     * (요청 자체는 거절된다) 짧을수록 좋다.
     */
    public static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * 왜 폐기됐나(#389) — 살아 있으면 null.
     *
     * <p>유예 창 복구가 <b>회전으로 폐기된 것만</b> 되살리게 하려고 든다. 시각만 보면 로그아웃도
     * "방금 폐기됨" 이라, 로그아웃한 뒤 10초 동안 그 토큰으로 세션이 되살아난다.
     *
     * <p>이 컬럼이 붙기 전의 행은 null 이다. 그것들은 복구 대상에서 빠지는데, 안전한 쪽이고 창이 10초라
     * 실제로 걸릴 행도 없다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 20)
    private RevokedReason revokedReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** 사용자에게 refresh 토큰을 발급한다. 인자는 이미 해시된 값이어야 한다(원문은 응답으로만 나간다). */
    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다");
        Objects.requireNonNull(tokenHash, "토큰 해시는 필수입니다");
        Objects.requireNonNull(expiresAt, "만료 시각은 필수입니다");
        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("토큰 해시는 비어 있을 수 없습니다");
        }
        return RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();
    }

    /** 회전·로그아웃으로 폐기한다. 이미 폐기됐으면 최초 폐기 시각·사유를 유지한다(재사용 감지의 근거). */
    public void revoke(Instant now, RevokedReason reason) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = Objects.requireNonNull(reason, "폐기 사유는 필수입니다");
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * <b>방금</b> 폐기됐는가 — 회전 직후 유예 창 안인지.
     *
     * <p>같은 refresh 가 다시 온 이유를 가른다. 창 안이면 정상 앱의 재시도·동시 요청이고, 창 밖이면 탈취 정황이다.
     * 판정을 서비스가 아니라 여기서 하는 이유는 {@code revokedAt} 이 이 객체의 상태이기 때문이다.
     */
    public boolean revokedWithin(Duration grace, Instant now) {
        return revokedAt != null && !revokedAt.isBefore(now.minus(grace));
    }

    /**
     * 유예 창 복구의 대상인가 — <b>회전으로, 방금</b> 폐기된 것만(#389).
     *
     * <p>사유를 함께 보지 않으면 로그아웃·탈취 폐기도 "방금 폐기됨" 이라 이 창을 통과한다. 그러면 끊으려고
     * 끊은 세션이 10초 동안 되살아난다 — 로그아웃이 그 시간만큼 듣지 않는다.
     */
    public boolean recoverableWithin(Duration grace, Instant now) {
        return revokedWithin(grace, now) && revokedReason != null && revokedReason.recoverable();
    }

    /**
     * 이 토큰이 다시 온 것이 <b>탈취 정황인가</b> — 아니면 그냥 죽은 토큰인가(#389).
     *
     * <p>사유를 모르는 옛 행은 정황으로 본다. 이 컬럼이 붙기 전의 폐기는 회전 아니면 로그아웃인데,
     * 둘을 가릴 수 없으면 <b>덜 끊는 쪽보다 더 끊는 쪽</b>이 안전하다.
     */
    public boolean reuseIsSuspicious() {
        return revokedReason == null || revokedReason.alarmsOnReuse();
    }

    /**
     * 이 사용자의 토큰인가 — <b>남의 세션을 끊지 못하게</b>(#389).
     *
     * <p>로그아웃이 본문으로 받은 refresh 를 그대로 믿고 폐기하면, 남의 토큰 원문을 아는 사람이
     * 그 사람을 로그아웃시킬 수 있다. access 로 확인한 주인과 같은지 여기서 가른다.
     */
    public boolean belongsTo(UUID candidate) {
        return userId.equals(candidate);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** 재발급에 쓸 수 있는 상태인지 — 폐기되지 않았고 만료되지도 않았을 때만. */
    public boolean isUsableAt(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
