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

    /** 이메일 최대 길이 — 닉네임과 같은 이유로 컬럼 폭과 맞춘다. */
    public static final int MAX_EMAIL_LENGTH = 255;

    /**
     * 프로필 사진 주소 최대 길이 — 컬럼 폭과 맞춘다.
     *
     * <p>Kakao CDN 주소는 쿼리스트링을 달고 오고 Google 사진 주소에도 크기 파라미터가 붙어 짧지 않다.
     * 넘치는 값이 저장 단계 서버 오류가 되지 않게 경계에서 자른다.
     */
    public static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;

    /** provider 도 요청도 이름을 주지 않았을 때의 표시 이름. Apple 로그인에서 실제로 발생한다. */
    private static final String DEFAULT_NICKNAME = "여행자";

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, length = MAX_NICKNAME_LENGTH)
    private String nickname;

    /**
     * provider 가 준 이메일. <b>없을 수 있다.</b>
     *
     * <p>Kakao 는 동의를 거부할 수 있고, Apple 은 Private Relay 익명 주소를 주거나 최초 로그인에만 준다. 그래서 NULL
     * 을 허용하고 계정 매칭에도 쓰지 않는다 — 매칭 키는 {@link UserIdentity} 의 provider 식별자뿐이다.
     */
    @Column(length = MAX_EMAIL_LENGTH)
    private String email;

    /**
     * provider 가 준 프로필 사진 주소. <b>없을 수 있다.</b>
     *
     * <p>Apple 은 사진을 아예 주지 않고, Kakao 는 동의를 거부할 수 있다. 이메일과 같은 이유로 NULL 을 허용하고,
     * <b>빈 문자열로 채우지 않는다</b> — 앱이 "없다" 와 "빈 값" 을 구분 못 해 깨진 이미지 자리를 그린다.
     */
    @Column(name = "profile_image_url", length = MAX_PROFILE_IMAGE_URL_LENGTH)
    private String profileImageUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private User(String nickname, String email, String profileImageUrl) {
        this.nickname = nickname;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 표시 이름과 이메일로 사용자를 만든다. 닉네임이 비었으면 기본 표시 이름으로, 길면 컬럼 폭에 맞게 자른다 — 닉네임
     * 하나 때문에 가입 자체가 실패하면 안 된다(provider 가 주는 값이라 우리가 통제하지 못한다). 이메일도 같은 이유로
     * 길이만 맞추고 형식을 강제하지 않는다.
     */
    public static User of(String nickname, String email) {
        return of(nickname, email, null);
    }

    /** 프로필 사진까지 함께 받아 만든다(#308) — 사진을 주는 provider 로 가입한 경로용. */
    public static User of(String nickname, String email, String profileImageUrl) {
        return new User(normalizeNickname(nickname), normalizeEmail(email), normalizeUrl(profileImageUrl));
    }

    /** 표시 이름만으로 만든다 — 이메일을 주지 않는 경로(개발 로그인)용. */
    public static User withNickname(String nickname) {
        return of(nickname, null);
    }

    /** 표시 이름 변경. 정규화 규칙은 생성과 같다. */
    public void rename(String nickname) {
        this.nickname = normalizeNickname(nickname);
    }

    /**
     * 프로필 사진 주소 교체(#308) — <b>빈 값으로는 지우지 않는다</b>.
     *
     * <p>로그인할 때마다 provider 값으로 갱신하는데, 동의를 잠시 껐거나 응답이 비어 온 회차에 기존 주소를
     * 지우면 멀쩡히 보이던 사진이 사라진다. 낡은 주소가 없는 주소보다 낫다 — 앱은 주소가 죽어 있으면
     * 기본 아이콘으로 되돌아간다.
     *
     * @return 실제로 값이 바뀌었으면 {@code true} — 안 바뀐 회차에 UPDATE 를 만들지 않으려는 신호다
     */
    public boolean changeProfileImage(String profileImageUrl) {
        String normalized = normalizeUrl(profileImageUrl);
        if (normalized == null || normalized.equals(this.profileImageUrl)) {
            return false;
        }
        this.profileImageUrl = normalized;
        return true;
    }

    private static String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        return truncate(nickname.strip(), MAX_NICKNAME_LENGTH);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return truncate(email.strip(), MAX_EMAIL_LENGTH);
    }

    /** 빈 값은 null 로 접는다 — 빈 문자열을 저장하면 앱이 "없다" 와 구분하지 못한다. */
    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return truncate(url.strip(), MAX_PROFILE_IMAGE_URL_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
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
