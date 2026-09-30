package com.offway.core.user.event;

import com.offway.core.common.notification.Notifier;
import com.offway.core.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자가 탈퇴하면 팀에게 한 줄 알린다.
 *
 * <h2>왜 필요한가 — 인원이 줄어드는 것이 안 보였다</h2>
 *
 * <p>가입만 알리고 있었다({@link SignupAlertOnUserRegistered}). 그래서 인원이 <b>늘어나는 것만</b> 보이고
 * 줄어드는 것은 안 보였다. 그 비대칭이 실제로 팀을 헷갈리게 했다.
 *
 * <p>2026-09-30 운영 로그가 그 장면이다.
 *
 * <pre>
 * 13:40  🙋 새 사용자 — GOOGLE · 현재 13명
 * 16:25:07  (탈퇴 — 알림 없음)
 * 16:25:11  🙋 새 사용자 — KAKAO · 현재 13명
 * </pre>
 *
 * <p>가입 둘에 인원이 그대로라 <b>알림이 고장난 것처럼 보였다.</b> 실제로는 그 사이 탈퇴가 있어 숫자가
 * 맞았는데, 탈퇴가 안 보이니 확인할 방법이 로그를 뒤지는 것뿐이었다.
 *
 * <p>탈퇴는 <b>DB 에 흔적을 남기지 않는다</b>({@code UserWithdrawalPersistenceService} 가 신원·토큰·사용자
 * 행을 다 지운다). 그래서 앱 로그가 유일한 기록이고, 컨테이너를 다시 띄우면 그것도 사라진다. 이 한 줄이
 * 남는 기록을 만든다.
 *
 * <h2>왜 이 리스너만 커밋 뒤인가</h2>
 *
 * <p>{@link UserWithdrawn} 의 다른 리스너 넷은 <b>같은 트랜잭션에서 동기로</b> 돈다. 데이터를 지우는
 * 일이라 부분 성공을 허용하지 않기 때문이다 — 하나라도 실패하면 탈퇴 전체가 롤백돼야 한다.
 *
 * <p>알림은 그 반대다. {@link TransactionPhase#AFTER_COMMIT} 을 쓰는 이유가 둘이다.
 *
 * <p><b>거짓을 알리지 않는다.</b> 트랜잭션 안에서 보내면 뒤에서 롤백됐을 때 "탈퇴했다" 는 알림만 남는다.
 * 그 사람은 그대로 있는데 팀은 나간 줄 안다.
 *
 * <p><b>트랜잭션이 외부 호출을 기다리지 않는다.</b> 디스코드는 외부이고 read-timeout 이 길다. 트랜잭션
 * 안에서 부르면 그 시간 동안 DB 커넥션을 잡아 풀이 마른다({@code persistence-convention}).
 *
 * <h2>인원이 이미 줄어든 값으로 나간다</h2>
 *
 * <p>{@code UserWithdrawn} 은 사용자 행을 <b>지우기 전에</b> 발행된다(도메인들이 먼저 자기 데이터를
 * 치워야 하므로). 그래도 이 리스너가 세는 값은 지워진 뒤의 인원이다 — {@code AFTER_COMMIT} 은 삭제까지
 * 커밋된 다음이라서다.
 *
 * <p>이 순서가 뒤집히면(예: 누가 이 리스너를 {@code BEFORE_COMMIT} 으로 바꾸면) 탈퇴 알림이 탈퇴 전
 * 인원을 말한다. 그러면 가입 알림과 숫자가 겹쳐 처음 문제로 돌아간다. 통합 테스트가 그 값을 고정한다.
 *
 * <h2>개인을 특정할 값을 싣지 않는다</h2>
 *
 * <p>{@code userId} 를 싣지 않는다. 디스코드 채널에 남으면 그 자체가 유출 경로이고, 가입 알림이 닉네임·
 * 이메일을 뺀 것과 같은 기준이다(#592).
 *
 * <p><b>provider 도 못 싣는다.</b> 가입 알림은 싣는데 여기는 없어 비대칭인데, 이유가 있다 — 커밋 뒤에는
 * 신원 행이 이미 지워져 조회할 데이터가 없다. 이벤트에 실어 나르려면 {@link UserWithdrawn} 의 모양을
 * 바꿔야 하는데, 그 record 는 "키가 {@code userId} 하나" 를 의도로 삼고 있다(#280). 관측 편의를 위해
 * 도메인 이벤트의 계약을 넓히지 않는다.
 *
 * <h2>실패해도 탈퇴를 막지 않는다</h2>
 *
 * <p>{@code Notifier} 자체는 예외를 안 던지고 비동기로 보내지만 <b>인원 조회</b>는 DB 를 탄다. 그쪽이
 * 실패해도 탈퇴는 이미 끝난 일이라 여기서 던지지 않는다 — 알림은 관측이지 기능이 아니다
 * ({@code SignupAlertOnUserRegistered}·{@code ExternalApiCallRecorder} 와 같은 규칙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalAlertOnUserWithdrawn {

    private final Notifier notifier;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserWithdrawn event) {
        try {
            notifier.send("👋 탈퇴 — 현재 %d명".formatted(userRepository.count()));
        } catch (RuntimeException e) {
            log.warn("탈퇴 알림 실패 cause={}", e.getClass().getSimpleName());
        }
    }
}
