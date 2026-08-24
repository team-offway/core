package com.offway.core.notification.domain;

import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import java.util.UUID;
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
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자에게 보여줄 알림 한 건(#263).
 *
 * <p><b>문구를 담지 않는다.</b> 종류({@link NotificationType})만 저장하고 화면 문구는 앱이 만든다. 문구를
 * 서버에 굳혀 두면 이미 쌓인 알림은 영영 옛 문구로 남아, 앱이 표현을 고칠 때 화면에 두 세대가 섞인다.
 *
 * <p><b>읽음을 boolean 이 아니라 시각으로 둔다.</b> 저장 비용이 같은데 "읽었다" 외에 "언제 읽었나" 까지
 * 답한다. 나중에 안 읽은 알림을 다시 밀어주는 규칙을 넣을 때 근거가 이미 있다.
 *
 * <p>{@code courseId} 는 도메인 경계를 넘는 참조라 <b>raw ID</b> 다(영속성 규약). 코스가 지워져도 알림은
 * 남아야 하므로 연관관계로 묶지 않는다 — 지워진 코스를 가리키는 알림은 눌러도 코스가 없을 뿐이다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    /** enum 이름을 담는 칸. 지금 가장 긴 이름의 두 배 남짓으로, 새 종류가 늘어도 마이그레이션이 필요 없다. */
    public static final int TYPE_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    /**
     * ordinal 이 아니라 이름으로 저장한다 — ordinal 은 상수를 재배치하는 순간 이미 저장된 행의 뜻이 통째로
     * 바뀐다. enum 이름은 클라이언트 계약이기도 하다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = TYPE_LENGTH)
    private NotificationType type;

    /** 누르면 이동할 코스. 코스와 무관한 알림도 생길 수 있어 없을 수 있다. */
    @Column(name = "course_id")
    private Long courseId;

    /** 읽은 시각. null 이면 안 읽음. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notification(UUID userId, NotificationType type, Long courseId, LocalDateTime createdAt) {
        this.userId = requireOwner(userId);
        this.type = Objects.requireNonNull(type, "알림 종류는 필수입니다");
        this.courseId = courseId;
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다");
    }

    /**
     * 소유자는 인증으로 확인된 사용자다 — <b>형식 검증이 필요 없다</b>(#280).
     *
     * <p>예전에는 길이·공백을 봤다. 소유 키가 요청 헤더({@code X-Guest-Id})라 아무 문자열이나 들어왔고,
     * 그 값이 그대로 컬럼에 저장돼 잘리거나 빈 값이 남았다. 지금은 {@code @LoginUser} 가 토큰에서 꺼낸
     * {@code UUID} 라 <b>형식이 이미 보장돼 있고, 무엇보다 남의 값을 적어 보낼 수 없다.</b>
     */
    public static UUID requireOwner(UUID userId) {
        return Objects.requireNonNull(userId, "사용자 ID는 필수입니다");
    }

    /**
     * 읽음 여부 — 응답의 {@code read} 와 안읽음 개수 판정이 이 값을 쓴다.
     *
     * <p><b>읽음으로 바꾸는 것은 엔티티가 하지 않는다.</b> 예전에는 여기에 {@code markRead} 가 있었는데,
     * 읽고 검사하고 쓰는 사이에 다른 요청이 끼면 나중 쪽이 처음 읽은 시각을 덮어썼다. 판정과 기록을 한
     * 문장으로 합쳐야 해서 조건부 UPDATE 로 옮겼다({@code NotificationRepository.markRead}).
     */
    public boolean isRead() {
        return readAt != null;
    }

    /** 누르면 이동할 코스. 없으면 비어 있다. */
    public Optional<Long> course() {
        return Optional.ofNullable(courseId);
    }
}
