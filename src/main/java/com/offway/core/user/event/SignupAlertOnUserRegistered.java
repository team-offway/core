package com.offway.core.user.event;

import com.offway.core.common.notification.Notifier;
import com.offway.core.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 새 사용자가 가입하면 팀에게 한 줄 알린다(#610).
 *
 * <h2>왜 알려야 하나</h2>
 *
 * <p>지금 나가는 알림 넷은 <b>전부 우리 쪽 사정</b>이다(한도·외부장애·코스사용량·정책). "사람이
 * 들어왔는가" 를 말해 주는 것이 없어서, 심사처럼 지켜봐야 하는 자리에서 로그를 뒤져야 안다.
 *
 * <p>그리고 이 알림은 <b>인증 경로가 산다는 신호</b>를 겸한다. provider 검증·토큰 발급·DB 쓰기가 전부
 * 통과해야 여기 닿는다 — 로그인이 깨져 있으면 이 줄이 안 온다.
 *
 * <h2>커밋 뒤에 보낸다</h2>
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} 이다. 이유가 둘이다.
 *
 * <p><b>거짓을 알리지 않는다.</b> 가입 트랜잭션이 뒤에서 롤백되면 그 사용자는 없는데 알림은 이미
 * 나갔다 — 되돌릴 수 없는 종류의 잘못이다.
 *
 * <p><b>트랜잭션이 외부 호출을 기다리지 않는다.</b> 디스코드는 외부이고 read-timeout 이 길다. 트랜잭션
 * 안에서 부르면 그 시간 동안 DB 커넥션을 잡아 풀이 마른다(`persistence-convention`).
 *
 * <h2>개인을 특정할 값을 싣지 않는다</h2>
 *
 * <p>닉네임·이메일·{@code userId} 를 싣지 않는다. 디스코드 채널에 남으면 그 자체가 유출 경로이고,
 * 여행지 평가를 익명으로 바꾼 판단(#592)과도 모순된다. <b>{@link UserRegistered} 가 provider 만 들고
 * 있어</b> 여기서 실을 수도 없다.
 *
 * <p>대신 <b>지금 인원</b>을 함께 싣는다. "몇 명째인가" 가 한 줄의 값어치를 크게 올리는데, 개인 식별력은
 * 없다.
 *
 * <h2>실패해도 가입을 막지 않는다</h2>
 *
 * <p>{@code Notifier} 자체가 예외를 안 던지고 비동기로 보내지만, <b>인원 조회</b>는 DB 를 탄다. 그쪽이
 * 실패해도 가입은 이미 끝난 일이라 여기서 던지지 않는다 — 알림은 관측이지 기능이 아니다
 * ({@code ExternalApiCallRecorder}·{@code FallbackKeyAlert} 와 같은 규칙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignupAlertOnUserRegistered {

    private final Notifier notifier;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserRegistered event) {
        try {
            notifier.send("🙋 새 사용자 — %s · 현재 %d명".formatted(event.provider(), userRepository.count()));
        } catch (RuntimeException e) {
            log.warn("가입 알림 실패 provider={} cause={}", event.provider(), e.getClass().getSimpleName());
        }
    }
}
