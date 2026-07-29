package com.offway.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * provider 계정 ↔ 우리 사용자 매핑.
 *
 * <p>매칭 키는 ID 토큰의 {@code sub} 뿐이다. 이메일로 매칭하면 안 된다 — Apple Private Relay 는 익명 주소를 주고,
 * Kakao 는 이메일 동의를 거부할 수 있어 값이 아예 없을 수 있다.
 *
 * <p>{@link User} 와 생명주기를 공유하지만 항상 같이 로드되지는 않아(로그인은 identity → user 단방향 조회가 주 경로)
 * JPA 연관관계 대신 raw {@code userId} 로 참조한다.
 */
@Entity
@Table(name = "user_identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private UserIdentity(UUID userId, AuthProvider provider, String providerUserId) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    /** 검증된 provider 신원을 우리 사용자에 연결한다. */
    public static UserIdentity link(UUID userId, AuthProvider provider, String providerUserId) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다");
        Objects.requireNonNull(provider, "provider 는 필수입니다");
        Objects.requireNonNull(providerUserId, "provider 사용자 ID는 필수입니다");
        if (providerUserId.isBlank()) {
            throw new IllegalArgumentException("provider 사용자 ID는 비어 있을 수 없습니다");
        }
        return new UserIdentity(userId, provider, providerUserId);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
