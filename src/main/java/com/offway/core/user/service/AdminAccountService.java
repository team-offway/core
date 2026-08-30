package com.offway.core.user.service;

import com.offway.core.user.domain.AdminAccount;
import com.offway.core.user.repository.AdminAccountRepository;
import com.offway.core.user.repository.UserIdentityRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 화이트리스트 조회(#342) — 다른 도메인은 <b>이 서비스를 통해서만</b> 어드민을 묻는다.
 *
 * <p>{@code user} 가 소유한다. "이 사람이 누구인가" 는 인증의 질문이고, provider 신원을 아는 것은 여기뿐이다.
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminAccountRepository adminAccountRepository;
    private final UserIdentityRepository userIdentityRepository;

    /**
     * 이 사용자를 감사 흔적에 적을 이름 — 어드민이 아니면 빈 값.
     *
     * <p><b>토큰의 역할을 다시 확인하지 않는다.</b> 여기까지 온 요청은 이미 {@code hasRole(ADMIN)} 을
     * 통과했다. 이 조회가 답하는 것은 권한이 아니라 <b>"뭐라고 적을 것인가"</b> 다.
     *
     * <p>userId 로 시작해 신원을 한 번 더 읽는다 — 화이트리스트의 키가 우리 사용자 id 가 아니라 provider
     * 식별자이기 때문이다. 쓰기 요청에서만 도는 조회라 두 번 읽는 비용이 문제되지 않는다.
     */
    @Transactional(readOnly = true)
    public Optional<String> labelOf(UUID userId) {
        return userIdentityRepository
                .findFirstByUserId(userId)
                .flatMap(identity -> adminAccountRepository.find(identity.getProvider(), identity.getProviderUserId()))
                .map(AdminAccount::getLabel);
    }
}
