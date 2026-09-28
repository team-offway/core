package com.offway.core.curation.event;

import com.offway.core.common.notification.Notifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 큐레이션 링크가 추가되면 팀에게 검토를 청한다(#613).
 *
 * <h2>왜 알려야 하나</h2>
 *
 * <p>큐레이션 링크는 <b>만들어도 기본이 비공개</b>다({@code published} 기본 false — "어드민이 만들다 만
 * 항목이 곧바로 사용자에게 보이면 안 된다"). 그 판단은 맞지만, 그래서 <b>하나 만들어 두면 꺼진 채로
 * 잊힌다.</b> 지금은 {@code log.info} 한 줄이 전부라 팀이 "이거 켤까요" 를 물을 계기가 없다.
 *
 * <p>가입 알림(#610)이 "사람이 들어왔다" 를 말해 주는 것과 같은 자리다 — 사람이 손을 써야 하는 일이
 * 생겼는데 아무 신호가 없던 곳이다.
 *
 * <h2>커밋 뒤에 보낸다</h2>
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} 이다. 롤백된 생성을 알리면 없는 항목을 검토하라는 말이 되고,
 * 트랜잭션 안에서 외부를 부르면 read-timeout 동안 DB 커넥션을 잡아 풀이 마른다(`persistence-convention`).
 *
 * <h2>주소는 백오피스 하나만 싣는다</h2>
 *
 * <p>링크의 실제 주소({@code link_url})는 안 싣는다 — 이유는 {@link CuratedLinkAdded} 에 적었다. 대신
 * 백오피스 주소를 실어 <b>검토할 자리로 데려간다.</b>
 *
 * <p><b>항목 딥링크는 못 만든다.</b> 어드민이 {@code /admin/} 정적 SPA 인데 프래그먼트를 로그인 토큰
 * 받는 데만 쓰고 읽은 즉시 지운다({@code harvestFragment}). 그래서 콘솔 루트로 보낸다 — 목록에서 제목으로
 * 찾게 하려고 {@code title} 을 문구에 싣는다.
 *
 * <h2>만든 사람을 싣지 않는다</h2>
 *
 * <p>어드민 라벨을 문구에 넣으면 채널에 사람 이름이 쌓인다. 누가 만들었는지는 감사 컬럼
 * ({@code updated_by})에 이미 남아 있고, 검토에 필요한 것은 "무엇을" 이지 "누가" 가 아니다.
 *
 * <h2>실패해도 생성을 막지 않는다</h2>
 *
 * <p>{@code Notifier} 가 예외를 안 던지는 것이 계약이지만 여기서 한 번 더 받는다 — 리스너가 던지면
 * {@code AFTER_COMMIT} 단계에서 로그만 남고 끝나긴 해도, <b>왜 못 보냈는지</b>가 그 로그에 안 남는다.
 * 알림은 관측이지 기능이 아니다.
 */
@Slf4j
@Component
public class CurationReviewAlertOnCuratedLinkAdded {

    private final Notifier notifier;

    /**
     * 백오피스 주소 — 알림을 받은 사람이 바로 열 자리다.
     *
     * <p>기본값을 운영 주소로 둔다. 로컬에서 이 알림이 뜨는 일은 드물고(웹훅이 없으면 구현이 로그만
     * 남긴다), 값을 비워 두면 문구에 빈 링크가 나간다.
     */
    private final String consoleUrl;

    public CurationReviewAlertOnCuratedLinkAdded(
            Notifier notifier,
            @Value("${offway.admin.console-url:https://api.offway.cloud/admin/}") String consoleUrl) {
        this.notifier = notifier;
        this.consoleUrl = consoleUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CuratedLinkAdded event) {
        try {
            notifier.send("""
                    🔗 큐레이션 링크 추가됨 — %s (게시 %s)
                    칩 문구: %s · 노출 면: %s
                    켤지 검토해 주세요 → %s"""
                    .formatted(
                            event.title(),
                            event.published() ? "ON" : "OFF",
                            event.chipText(),
                            event.surfaces(),
                            consoleUrl));
        } catch (RuntimeException e) {
            log.warn("큐레이션 링크 알림 실패 title={} cause={}", event.title(), e.getClass().getSimpleName());
        }
    }
}
