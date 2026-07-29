package com.offway.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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
        return new RefreshToken(userId, tokenHash, expiresAt);
    }

    /** 회전·로그아웃으로 폐기한다. 이미 폐기됐으면 최초 폐기 시각을 유지한다(재사용 감지의 근거). */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
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
