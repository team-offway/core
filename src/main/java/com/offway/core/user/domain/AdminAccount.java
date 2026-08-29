package com.offway.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 백오피스를 쓸 수 있는 계정 화이트리스트(#342).
 *
 * <p><b>DB 테이블로 두는 이유</b> — 환경변수에 이메일을 박으면 사람이 늘 때마다 배포해야 하는데, 그건
 * "배포 없이 고친다" 는 이 에픽(#340)의 취지와 정면으로 모순된다.
 *
 * <p><b>이메일이 아니라 provider 식별자로 맞춘다.</b> {@link UserIdentity} 와 같은 이유다 — Apple Private
 * Relay 는 익명 주소를 주고 Kakao 는 이메일 동의를 거부할 수 있어, 이메일은 없거나 바뀔 수 있는 값이다.
 *
 * <p>여기 있는 것은 <b>권한뿐이고 사용자가 아니다.</b> 로그인은 평소대로 소셜로 하고, 이 표에 있으면
 * 발급되는 토큰에 {@link AccountRole#ADMIN} 이 함께 실린다.
 */
@Entity
@Table(name = "admin_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    /**
     * 사람이 알아볼 이름 — 감사 흔적({@code curated_link.updated_by})에 그대로 남는다.
     *
     * <p>배포 없이 값을 고칠 수 있게 되면 <b>누가 언제 바꿨는지가 유일한 추적 수단</b>이 된다. seed SQL
     * 시절에는 git blame 이 그 역할을 했다.
     */
    @Column(nullable = false, length = 50)
    private String label;

    @Builder
    private AdminAccount(AuthProvider provider, String providerUserId, String label) {
        this.provider = Objects.requireNonNull(provider, "provider 는 필수입니다");
        this.providerUserId = requireText(providerUserId, "provider 식별자");
        this.label = requireText(label, "label");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "은(는) 필수입니다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 비어 있을 수 없습니다");
        }
        return value.strip();
    }
}
