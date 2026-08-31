package com.offway.core.notification.service.dto;

import com.offway.core.notification.domain.NotificationType;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * 푸시로 내보낼 알림 하나(#270) — 소유자와 내용만.
 *
 * <p>엔티티를 그대로 넘기지 않는다. 발송은 <b>트랜잭션 밖</b>에서 일어나므로 그때 엔티티는 이미 준영속
 * 이고, 무심코 연관을 건드리면 그 자리에서 터진다. 발송에 실제로 필요한 것만 담는다.
 *
 * @param userId 받을 사람 — 인증으로 확인된 소유자다(#280). 이 사람의 기기 토큰으로 보낸다
 * @param type 알림 종류. 앱이 아이콘·문구를 맞추는 키다
 * @param courseId 누르면 이동할 코스. 없으면 null
 * @param notificationId <b>어느 알림인가</b>(#357). 배너를 눌러 들어온 앱이 그 자리에서 읽음 처리하려면
 *     이 값이 있어야 한다. {@code courseId} 로는 대신할 수 없다 — 한 코스에 여러 종류의 알림이 달려
 *     어느 것을 읽음 처리할지 정할 수 없다
 */
@Builder
public record PushTarget(UUID userId, NotificationType type, Long courseId, Long notificationId) {

    public PushTarget {
        Objects.requireNonNull(userId, "받는 사람은 null 일 수 없습니다.");
        Objects.requireNonNull(type, "알림 종류는 null 일 수 없습니다.");
        // 저장된 알림에서만 만들어지는 값이라 여기 닿을 때는 이미 id 가 있다. 비어 있다면 만드는 쪽이
        // 저장 전에 불렀다는 뜻이고, 그대로 두면 앱이 읽음 처리를 못 하는 알림이 조용히 섞인다.
        Objects.requireNonNull(notificationId, "알림 id 는 null 일 수 없습니다.");
    }
}
