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

    /**
     * provider 가 준 갱신 토큰 — 탈퇴 시 연결 해제에 쓴다(#287).
     *
     * <p><b>우리 refresh 토큰이 아니다.</b> 그쪽은 우리가 발급하고 회전시키지만, 이건 Apple 이 발급한 남의
     * 토큰이고 우리는 보관했다가 해제할 때 돌려줄 뿐이다.
     *
     * <p>null 이 정상이다 — Apple 이 아닌 provider 와, 이 컬럼이 생기기 전에 로그인한 사용자는 비어 있다.
     * 소급해서 채울 수 없다({@code authorizationCode} 는 1회용·5분). 재로그인하면 채워진다.
     *
     * <h2>평문으로 둔다 — 그 판단의 근거</h2>
     *
     * <p><b>해시할 수 없다.</b> 우리 refresh 토큰은 대조만 하면 되므로 해시해서 넣지만({@code hashRefreshToken}),
     * 이건 Apple 에 <b>원문 그대로 되돌려줘야</b> 해제가 된다.
     *
     * <p><b>이 값만으로는 아무것도 못 한다.</b> Apple 의 {@code /auth/token}·{@code /auth/revoke} 는 둘 다
     * {@code client_secret} 을 함께 요구하고, 그것은 우리 {@code .p8} 로 서명해야 만들어진다. 그 키는 DB 가 아니라
     * 환경변수·배포 시크릿에 있다 — <b>DB 나 백업만 새면 이 토큰은 쓸 수 없다.</b> 둘이 함께 새야 성립한다.
     *
     * <p>그래서 지금은 접근 통제(DB 는 EC2 내부에서만 닿고 백업도 같은 경계)에 기대고 평문으로 둔다.
     * <b>다만 방어를 하나 더 두는 것이 맞다</b> — 애플리케이션 레벨 암호화는 키 보관·회전을 어디에 둘지부터
     * 정해야 해서 이 PR(심사 대응)의 범위와 다르다. <b>#301</b> 로 옮겼다.
     */
    @Column(name = "provider_refresh_token", length = 512)
    private String providerRefreshToken;

    /**
     * 그 토큰을 발급받은 클라이언트.
     *
     * <p>해제할 때 <b>같은 클라이언트로 서명</b>해야 Apple 이 받아준다. 네이티브는 Bundle ID, 웹은 Service ID
     * 라 값이 갈리는데, 어느 쪽이었는지는 발급 시점에만 알 수 있어 함께 남긴다.
     */
    @Column(name = "provider_client_id")
    private String providerClientId;

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

    /**
     * 연결 해제에 쓸 토큰을 기억한다 — 로그인 그 순간에만 얻을 수 있다.
     *
     * <p>둘 중 하나라도 비면 아무것도 바꾸지 않는다. 반쪽만 남으면 해제할 때 클라이언트를 몰라 서명이
     * 틀리고, 그건 토큰이 아예 없는 것과 결과가 같으면서 원인만 찾기 어렵다.
     */
    public void rememberProviderToken(String refreshToken, String clientId) {
        if (refreshToken == null || refreshToken.isBlank() || clientId == null || clientId.isBlank()) {
            return;
        }
        this.providerRefreshToken = refreshToken;
        this.providerClientId = clientId;
    }

    /** 연결을 끊을 수 있는가 — 토큰과 클라이언트가 둘 다 있어야 한다. */
    public boolean revocable() {
        return providerRefreshToken != null && providerClientId != null;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
