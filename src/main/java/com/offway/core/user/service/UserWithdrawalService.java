package com.offway.core.user.service;

import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.apple.AppleAccountLink;
import com.offway.core.user.service.UserWithdrawalPersistenceService.ProviderLink;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴 — 계정과 그 사람의 데이터를 지우고, provider 연결을 끊는다.
 *
 * <p>App Store 심사 필수 항목이고(계정을 만들 수 있으면 앱 안에서 지울 수도 있어야 한다), 개인정보처리방침
 * 9항이 "앱 내 [마이 → 회원탈퇴]" 로 이미 약속한 경로다. 방침에 적힌 권리는 실제로 동작해야 한다.
 *
 * <p><b>Apple 은 우리 DB 를 지우는 것만으로 부족하다</b>(#287). 토큰 revoke 를 요구하며(심사 5.1.1(v)),
 * 안 하면 Apple 의 '이 App으로 로그인' 목록에 그대로 남는다.
 *
 * <p><b>이 빈은 순서만 소유한다.</b> DB 작업은 {@link UserWithdrawalPersistenceService}, 외부 호출은
 * {@link AppleAccountLink} 다. 한 빈에 두면 같은 객체 안의 {@code @Transactional} 호출이 proxy 를 안 거쳐
 * 트랜잭션이 조용히 무력화된다(persistence-convention §self-invocation).
 *
 * <p><b>유예 기간을 두지 않았다(soft delete 아님).</b> 즉시 삭제다. 되돌리기를 지원하려면 "탈퇴했지만 아직
 * 살아 있는 계정" 이라는 상태가 생기고, 그 상태에서 로그인·조회·재가입이 각각 어떻게 되어야 하는지를 전부
 * 정해야 한다. 지금 그 요구가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserWithdrawalPersistenceService persistenceService;
    private final AppleAccountLink appleAccountLink;

    /**
     * 탈퇴시킨다.
     *
     * <p><b>지울 대상은 서버가 안다.</b> 코스·연차는 아직 {@code guest_id} 로 묶여 있지만, 로그인할 때 그 기기를
     * 사용자에게 이어 뒀다({@code user_guest_link}). 그래서 요청이 헤더를 안 들고 와도 데이터를 찾을 수 있고,
     * 반대로 <b>헤더에 남의 값을 적어 보내도 남의 데이터는 지워지지 않는다</b> — 기록된 것만 대상이다.
     *
     * <p>순서가 정해져 있다.
     *
     * <ol>
     *   <li><b>읽기</b> — 신원이 사라지면 무엇으로 연결을 끊을지 알 수 없다.
     *   <li><b>삭제</b>(트랜잭션) — 여기까지 성공하면 사용자에게는 탈퇴가 끝난 것이다.
     *   <li><b>연결 해제</b>(트랜잭션 밖) — 실패해도 되돌리지 않는다.
     * </ol>
     *
     * <p>해제를 마지막에 두는 이유: 먼저 끊고 삭제가 실패하면 <b>계정은 남았는데 Apple 로 다시 로그인할 수
     * 없는</b> 상태가 된다. 반대 순서의 실패(계정은 지워졌고 Apple 목록에만 남음)는 사용자가 Apple 설정에서
     * 직접 정리할 수 있다.
     *
     * @param userId 인증으로 확인된 사용자 — 자기 계정만 지울 수 있다
     * @throws UserException 이미 탈퇴한 계정이면 {@code USER-006}
     */
    public void withdraw(UUID userId) {
        Optional<ProviderLink> appleLink = persistenceService.appleLinkOf(userId);

        persistenceService.deleteAccount(userId);

        appleLink.ifPresentOrElse(
                link -> revokeQuietly(userId, link),
                // 이 기능 이전에 로그인했거나 옛 앱이다. 소급해서 채울 수 없어(코드는 1회용·5분) 정상 경로다.
                () -> log.debug("Apple 갱신 토큰이 없어 연결 해제를 건너뜁니다 userId={}", userId));
    }

    /**
     * Apple 연결을 끊는다 — <b>실패해도 이미 끝난 탈퇴를 되돌리지 않는다</b>.
     *
     * <p>못 끊으면 사용자가 Apple 설정에서 직접 지워야 한다. Apple 이 문서화한 대안이고(TN3194), 우리 쪽
     * 데이터는 이미 지워졌다. 다만 <b>왜 못 했는지는 남긴다</b> — 이 로그가 쌓이면 심사 항목이 사실상 빠진 것이다.
     */
    private void revokeQuietly(UUID userId, ProviderLink link) {
        if (appleAccountLink.revoke(link.refreshToken(), link.clientId())) {
            log.info("Apple 연결 해제 완료 userId={}", userId);
            return;
        }
        log.warn("Apple 연결을 끊지 못했습니다 — 계정은 지워졌고 Apple 목록에는 남습니다 userId={}", userId);
    }
}
