package com.offway.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 서비스 사용자. 인증 수단은 {@link UserIdentity} 가 따로 들고, 이 엔티티는 신원과 표시 정보만 갖는다.
 *
 * <p>식별자가 UUID 라 순번 노출·열거 문제가 없고, 내부 PK 와 외부 노출 식별자를 하나로 쓴다. 랜덤 v4 대신 시간정렬
 * UUID({@code Style.TIME})를 쓰는 이유는 InnoDB 클러스터드 인덱스 파편화를 피하기 위해서다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /** 닉네임 최대 길이 — {@code nickname} 컬럼 폭과 일치시켜, 초과 입력이 저장 단계 서버 오류로 새지 않게 경계에서 자른다. */
    public static final int MAX_NICKNAME_LENGTH = 50;

    /** provider 도 요청도 이름을 주지 않았을 때의 표시 이름. Apple 로그인에서 실제로 발생한다. */
    private static final String DEFAULT_NICKNAME = "여행자";

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, length = MAX_NICKNAME_LENGTH)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private User(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 닉네임으로 사용자를 만든다. 값이 비었으면 기본 표시 이름으로, 길면 컬럼 폭에 맞게 자른다 — 닉네임 하나 때문에 가입 자체가
     * 실패하면 안 된다(provider 가 주는 값이라 우리가 통제하지 못한다).
     */
    public static User withNickname(String nickname) {
        return new User(normalize(nickname));
    }

    /** 표시 이름 변경. 정규화 규칙은 생성과 같다. */
    public void rename(String nickname) {
        this.nickname = normalize(nickname);
    }

    private static String normalize(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        String trimmed = nickname.strip();
        return trimmed.length() > MAX_NICKNAME_LENGTH ? trimmed.substring(0, MAX_NICKNAME_LENGTH) : trimmed;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
