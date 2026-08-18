package com.offway.core.notification.service.dto;

import com.offway.core.notification.domain.NotificationType;

/**
 * 푸시로 내보낼 알림 하나(#270) — 소유자와 내용만.
 *
 * <p>엔티티를 그대로 넘기지 않는다. 발송은 <b>트랜잭션 밖</b>에서 일어나므로 그때 엔티티는 이미 준영속
 * 이고, 무심코 연관을 건드리면 그 자리에서 터진다. 발송에 실제로 필요한 것은 셋뿐이다.
 *
 * @param guestId 받을 사람 — 이 사람의 기기 토큰으로 보낸다
 * @param type 알림 종류. 앱이 아이콘·문구를 맞추는 키다
 * @param courseId 누르면 이동할 코스. 없으면 null
 */
public record PushTarget(String guestId, NotificationType type, Long courseId) {
}
