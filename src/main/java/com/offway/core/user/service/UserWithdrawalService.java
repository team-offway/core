package com.offway.core.user.service;

import com.offway.core.user.domain.UserException;
import com.offway.core.user.event.UserWithdrawn;
import com.offway.core.user.repository.RefreshTokenRepository;
import com.offway.core.user.repository.UserIdentityRepository;
import com.offway.core.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 — 계정과 그 사람의 데이터를 지운다.
 *
 * <p>App Store 심사 필수 항목이고(계정을 만들 수 있으면 앱 안에서 지울 수도 있어야 한다), 개인정보처리방침
 * 9항이 "앱 내 [마이 → 회원탈퇴]" 로 이미 약속한 경로다. 방침에 적힌 권리는 실제로 동작해야 한다.
 *
 * <p><b>전부 한 트랜잭션이다.</b> 도메인별 정리는 {@link UserWithdrawn} 리스너들이 하는데, 동기로 돌아 같은
 * 트랜잭션에 참여한다. 하나라도 실패하면 통째로 롤백되고 사용자는 실패를 본다 — 부분 성공하면 사용자 행만
 * 사라지고 코스·연차가 <b>소유자 없이 남아 다시는 지울 수 없는</b> 데이터가 된다.
 *
 * <p>순서가 중요하다. 이벤트를 먼저 보내 도메인들이 자기 데이터를 치운 뒤에 계정을 지운다.
 *
 * <p><b>유예 기간을 두지 않았다(soft delete 아님).</b> 즉시 삭제다. 되돌리기를 지원하려면 "탈퇴했지만 아직
 * 살아 있는 계정" 이라는 상태가 생기고, 그 상태에서 로그인·조회·재가입이 각각 어떻게 되어야 하는지를 전부
 * 정해야 한다. 지금 그 요구가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 탈퇴시킨다.
     *
     * <p><b>지울 대상은 {@code userId} 하나가 정한다(#280).</b> 코스·연차·후기·알림이 전부 이 키로 묶여 있어,
     * 요청이 무엇을 들고 오든 지워지는 것은 인증으로 확인된 이 사용자의 데이터뿐이다. 예전에는 게스트 키를
     * 거쳐야 했고, 그 키가 검증 불가능한 헤더에서 왔기 때문에 남의 데이터를 지울 수 있었다.
     *
     * @param userId 인증으로 확인된 사용자 — 자기 계정만 지울 수 있다
     * @throws UserException 이미 탈퇴한 계정이면 {@code USER-006}
     */
    @Transactional
    public void withdraw(UUID userId) {
        // 토큰은 유효한데 계정이 없는 경우가 실제로 있다 — access 가 무상태라 탈퇴 후 만료까지 살아 있다.
        // 없는 계정에 삭제를 또 태우면 200 이 나가 앱이 "탈퇴됐다" 고 오해한다.
        if (userRepository.findById(userId).isEmpty()) {
            throw UserException.withdrawnUser();
        }
        eventPublisher.publishEvent(new UserWithdrawn(userId));
        int identities = userIdentityRepository.deleteByUserId(userId);
        int tokens = refreshTokenRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
        // 식별자만 남긴다 — 닉네임·이메일은 지우는 마당에 로그로 옮겨 적을 이유가 없다.
        log.info("회원 탈퇴 완료 userId={} 신원 {}건 · refresh {}건 삭제", userId, identities, tokens);
    }
}
