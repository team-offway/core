package com.offway.core.notification.domain;

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

    /** 소유 키 길이 — 코스({@code Course.MAX_GUEST_ID_LENGTH})·연차와 같은 값을 쓴다. */
    public static final int MAX_OWNER_ID_LENGTH = 64;

    /** enum 이름을 담는 칸. 지금 가장 긴 이름의 두 배 남짓으로, 새 종류가 늘어도 마이그레이션이 필요 없다. */
    public static final int TYPE_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false, length = MAX_OWNER_ID_LENGTH)
    private String guestId;

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
    private Notification(String guestId, NotificationType type, Long courseId, LocalDateTime createdAt) {
        this.guestId = requireOwner(guestId);
        this.type = Objects.requireNonNull(type, "알림 종류는 필수입니다");
        this.courseId = courseId;
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다");
    }

    /**
     * 소유 키 계약 검증(400).
     *
     * <p>빈 헤더({@code X-Guest-Id: " "})는 {@code @RequestHeader} 를 통과하므로 <b>멀쩡한 클라이언트가
     * 정상 요청으로 닿는다</b> — 불변식으로 다루면 500 이 나간다.
     *
     * <p>도메인이 들고 조회 경로도 이걸 쓴다. 조회만 통과시키면 같은 헤더가 메서드에 따라 200 과 400 으로
     * 갈린다.
     */
    public static String requireOwner(String guestId) {
        if (guestId == null || guestId.isBlank() || guestId.length() > MAX_OWNER_ID_LENGTH) {
            throw NotificationException.invalidOwnerId();
        }
        return guestId;
    }

    /**
     * 읽음으로 바꾼다 — <b>이미 읽었으면 아무것도 하지 않는다</b>.
     *
     * <p>재요청을 409 로 막지 않는 이유: 사용자가 원한 상태("읽음")가 이미 이뤄져 있다. 알림 화면은 스크롤
     * 중에 같은 요청을 두 번 보내기 쉬운 자리라, 두 번째를 실패로 만들면 화면이 오류를 띄운다.
     *
     * <p>처음 읽은 시각을 덮어쓰지 않는다.
     *
     * @return 이 호출로 실제 바뀌었으면 true
     */
    public boolean markRead(LocalDateTime readAt) {
        Objects.requireNonNull(readAt, "읽은 시각은 필수입니다");
        if (isRead()) {
            return false;
        }
        this.readAt = readAt;
        return true;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 누르면 이동할 코스. 없으면 비어 있다. */
    public Optional<Long> course() {
        return Optional.ofNullable(courseId);
    }
}
