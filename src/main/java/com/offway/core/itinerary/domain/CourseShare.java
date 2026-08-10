package com.offway.core.itinerary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 코스 공유 링크(#143) — 인증 없이 코스 하나를 읽게 해주는 추측 불가능한 토큰.
 *
 * <p><b>코스가 아니라 여기에 토큰을 둔다.</b> 코스는 hard delete 라, 토큰이 코스 컬럼이면 코스가 지워질 때
 * 함께 사라져 "없는 링크" 와 "게시자가 삭제한 코스" 를 구분할 수 없다. 이 행을 남겨두면 <b>행 자체가 묘비</b>가
 * 되어 추가 상태 없이 둘이 갈린다 — 토큰이 있는데 코스가 없으면 삭제된 것이다.
 *
 * <p>덤으로 토큰이 코스 수명과 분리돼, 나중에 만료·비활성화를 열 때 컬럼 하나만 더하면 된다.
 */
@Entity
@Table(name = "course_share")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseShare {

    /**
     * 토큰 엔트로피 — 128비트.
     *
     * <p>링크를 아는 사람만 볼 수 있다는 것이 유일한 접근 통제라, 토큰이 곧 자격증명이다. 짧게 잡아
     * 전수 대입이 가능해지면 순번 id 를 노출한 것과 다를 바 없어진다. URL-safe base64 로 22자가 된다.
     */
    private static final int TOKEN_BYTES = 16;

    /** 링크에 실리므로 URL-safe 여야 하고, 패딩({@code =})은 경로에서 지저분해 뺀다. */
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** 토큰은 자격증명이라 {@link SecureRandom} 이다 — 예측 가능한 난수면 링크를 추측할 수 있다. */
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_token", nullable = false, unique = true, length = 32)
    private String shareToken;

    /** 공유 대상 코스(raw 참조). 코스가 지워져도 이 값은 남는다 — 그것이 삭제됐다는 증거다. */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private CourseShare(String shareToken, Long courseId, LocalDateTime createdAt) {
        this.shareToken = shareToken;
        this.courseId = courseId;
        this.createdAt = createdAt;
    }

    /**
     * 코스에 붙일 새 공유 링크. 토큰은 입력에서 계산되는 값이라 빌더로 열지 않는다(조립이면 빌더, 계산이면 팩토리).
     *
     * <p>토큰을 밖에서 주입받지 않는 이유: 열어두면 호출자가 짧거나 예측 가능한 값을 넣을 수 있고, 그러면
     * 이 클래스가 보장하려던 "추측 불가능" 이 호출부마다 달라진다.
     */
    public static CourseShare issue(Long courseId, LocalDateTime createdAt) {
        Objects.requireNonNull(courseId, "courseId 는 필수입니다");
        Objects.requireNonNull(createdAt, "createdAt 은 필수입니다");
        return new CourseShare(newToken(), courseId, createdAt);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }
}
